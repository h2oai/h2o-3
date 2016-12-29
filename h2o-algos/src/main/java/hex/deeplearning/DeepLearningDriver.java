package hex.deeplearning;

import hex.DataInfo;
import hex.Driver;
import hex.ModelBuilder;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.MRUtils;
import water.util.PrettyPrint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static water.util.MRUtils.sampleFrame;
import static water.util.MRUtils.sampleFrameStratified;

/**
 * Created by vpatryshev on 12/29/16.
 */
public class DeepLearningDriver extends Driver {

  private DeepLearning deepLearning;
  private ModelBuilder mb;

  public DeepLearningDriver(DeepLearning deepLearning) {
    super((ModelBuilder)deepLearning);
    this.deepLearning = deepLearning;
    this.mb = (ModelBuilder) deepLearning; 
    deepLearning.checkMyConditions();
  }

  @Override
  public void computeImpl() {
    deepLearning.init(true); //this can change the seed if it was set to -1 
    long cs = deepLearning.params().checksum();
    // Something goes wrong
    if (mb.error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder((ModelBuilder) deepLearning);
    buildModel();
    //check that _parms isn't changed during DL model training
    long cs2 = deepLearning.params().checksum();
    assert (cs == cs2);

  }

  public DeepLearningModel modelWeBuild = null;

  /**
   * Train a Deep Learning model, assumes that all members are populated
   * If checkpoint == null, then start training a new model, otherwise continue from a checkpoint
   */
  public final void buildModel() {

    List<Key> removeMe = new ArrayList<>();
    if (deepLearning.params()._checkpoint == null) {
      modelWeBuild = new DeepLearningModel(mb.dest(), deepLearning.params(), new DeepLearningModelOutput(deepLearning), deepLearning.train(), deepLearning.valid(), deepLearning.nclasses());
      if (deepLearning.params()._pretrained_autoencoder != null) {
        final DeepLearningModel pretrained = DKV.getGet(deepLearning.params()._pretrained_autoencoder);
        if (pretrained == null)
          throw new H2OIllegalArgumentException("The pretrained model '" + deepLearning.params()._pretrained_autoencoder + "' cannot be found.");
        if (deepLearning.params()._autoencoder || !pretrained._parms._autoencoder)
          throw new H2OIllegalArgumentException("The pretrained model must be unsupervised (an autoencoder), and the model to be trained must be supervised.");
        Log.info("Loading model parameters of input and hidden layers from the pretrained autoencoder model.");
        modelWeBuild.model_info().initializeFromPretrainedModel(pretrained.model_info());
      } else {
        modelWeBuild.model_info().initializeMembers(deepLearning.params()._initial_weights, deepLearning.params()._initial_biases);
      }
    } else {
      final DeepLearningModel previous = DKV.getGet(deepLearning.params()._checkpoint);
      if (previous == null) throw new IllegalArgumentException("Checkpoint not found.");
      Log.info("Resuming from checkpoint.");
      deepLearning.job().update(0, "Resuming from checkpoint");

      if (deepLearning.isClassifier() != previous._output.isClassifier())
        throw new H2OIllegalArgumentException("Response type must be the same as for the checkpointed model.");
      if (deepLearning.isSupervised() != previous._output.isSupervised())
        throw new H2OIllegalArgumentException("Model type must be the same as for the checkpointed model.");

      //READ ONLY
      DeepLearningParameters.Sanity.checkIfParameterChangeAllowed(previous._parms, deepLearning.params());
      System.out.println("TR SRC DL X: " + deepLearning.train().name(0) + ".." + deepLearning.train().lastVecName());

      try {
        // PUBDEV-2513: Adapt _train and _valid (in-place) to match the frames that were used for the previous model
        // This can add or remove dummy columns (can happen if the dataset is sparse and datasets have different non-const columns)
        for (String st : previous.adaptTestForTrain(deepLearning.train(), true, false)) Log.warn(st);
        for (String st : previous.adaptTestForTrain(deepLearning.valid(), true, false)) Log.warn(st);
        DataInfo dinfo = DeepLearningBig.makeDataInfo(deepLearning.train(), deepLearning.valid(), deepLearning.params(), deepLearning.nclasses());
        DKV.put(dinfo); // For FrameTask that needs DataInfo in the DKV as a standalone thing - the DeepLearningModel has its own copy inside itself
        removeMe.add(dinfo._key);
        modelWeBuild = new DeepLearningModel(deepLearning.dest(), deepLearning.params(), previous, false, dinfo);
        modelWeBuild.write_lock(deepLearning.job());

        if (!Arrays.equals(modelWeBuild._output._names, previous._output._names)) {
          throw new H2OIllegalArgumentException("The columns of the training data must be the same as for the checkpointed model. Check ignored columns (or disable ignore_const_cols).");
        }
        if (!Arrays.deepEquals(modelWeBuild._output._domains, previous._output._domains)) {
          throw new H2OIllegalArgumentException("Categorical factor levels of the training data must be the same as for the checkpointed model.");
        }
        if (dinfo.fullN() != previous.model_info().data_info().fullN()) {
          throw new H2OIllegalArgumentException("Total number of predictors is different than for the checkpointed model.");
        }
        if (deepLearning.params()._epochs <= previous.epoch_counter) {
          throw new H2OIllegalArgumentException("Total number of epochs must be larger than the number of epochs already trained for the checkpointed model (" + previous.epoch_counter + ").");
        }

        // these are the mutable parameters that are to be used by the model (stored in model_info.parameters)
        final DeepLearningParameters actualParms = modelWeBuild.model_info().get_params(); //actually used parameters for model building (defaults filled in, etc.)
        assert (actualParms != previous.model_info().get_params());
        assert (actualParms != deepLearning.params());
        assert (actualParms != previous._parms);

        // Update actualNewP parameters based on what the user wants (cp_modifiable parameters only), was cloned from the previous model so far

        //show the user only the changes in the user-facing parameters
        DeepLearningParameters.Sanity.updateParametersDuringCheckpointRestart(deepLearning.params(), previous._parms, false /*doIt*/, false /*quiet*/);

        //actually change the parameters in the "insider" version of parameters
        DeepLearningParameters.Sanity.updateParametersDuringCheckpointRestart(deepLearning.params() /*user-given*/, modelWeBuild.model_info().get_params() /*model_info.parameters that will be used*/, true /*doIt*/, true /*quiet*/);

        // update/sanitize parameters (in place) to set defaults etc.
        DeepLearningParameters.Sanity.modifyParms(deepLearning.params(), modelWeBuild.model_info().get_params(), deepLearning.nclasses());

        Log.info("Continuing training after " + String.format("%.3f", previous.epoch_counter) + " epochs from the checkpointed model.");
        modelWeBuild.update(deepLearning.job());
      } catch (H2OIllegalArgumentException ex) {
        if (modelWeBuild != null) {
          modelWeBuild.unlock(deepLearning.job());
          modelWeBuild.delete();
          modelWeBuild = null;
        }
        throw ex;
      } finally {
        if (modelWeBuild != null) modelWeBuild.unlock(deepLearning.job());
      }
    }

    trainModel(modelWeBuild);
    for (Key k : removeMe) DKV.remove(k);

    // clean up, but don't delete weights and biases if user asked for export
    List<Key> keep = new ArrayList<>();
    try {
      if (deepLearning.params()._export_weights_and_biases && modelWeBuild._output.weights != null && modelWeBuild._output.biases != null) {
        for (Key k : Arrays.asList(modelWeBuild._output.weights)) {
          keep.add(k);
          for (Vec vk : ((Frame) DKV.getGet(k)).vecs()) {
            keep.add(vk._key);
          }
        }
        for (Key k : Arrays.asList(modelWeBuild._output.biases)) {
          keep.add(k);
          for (Vec vk : ((Frame) DKV.getGet(k)).vecs()) {
            keep.add(vk._key);
          }
        }
      }
    } finally {
      Scope.exit(keep.toArray(new Key[keep.size()]));
    }
  }


  /**
   * Train a Deep Learning neural net model
   *
   * @param model Input model (e.g., from initModel(), or from a previous training run)
   * @return Trained model
   */
  public final DeepLearningModel trainModel(DeepLearningModel model) {

    try {
//      if (checkpoint == null && !quiet_mode) logStart(); //if checkpoint is given, some Job's params might be uninitialized (but the restarted model's parameters are correct)
      if (model == null) {
        model = DKV.get(deepLearning.dest()).get();
      }
      Frame validScoreFrame = null;
      Frame train, trainScoreFrame;

      Log.info("Model category: " + (deepLearning.params()._autoencoder ? "Auto-Encoder" : deepLearning.isClassifier() ? "Classification" : "Regression"));
      final long model_size = model.model_info().size();
      Log.info("Number of model parameters (weights/biases): " + String.format("%,d", model_size));
      model.write_lock(deepLearning.job());
      deepLearning.job().update(0, "Setting up training data...");
      final DeepLearningParameters mp = model.model_info().get_params();
      DlInput td = mp.trainData;
      DlInput vd = mp.testData;
      // temporary frames of the same "name" as the orig _train/_valid (asking the parameter's Key, not the actual frame)
      // Note: don't put into DKV or they would overwrite the _train/_valid frames!
      Frame val_fr = deepLearning.valid() != null ? new Frame(mp._valid, deepLearning.valid().names(), deepLearning.valid().vecs()) : null;

      train = new Frame(mp._train, deepLearning.train().names(), deepLearning.train().vecs());
      if (model._output.isClassifier() && mp._balance_classes) {
        deepLearning.job().update(0, "Balancing class distribution of training data...");
        float[] trainSamplingFactors = new float[train.lastVec().domain().length]; //leave initialized to 0 -> will be filled up below
        if (mp._class_sampling_factors != null) {
          if (mp._class_sampling_factors.length != train.lastVec().domain().length)
            throw new IllegalArgumentException("class_sampling_factors must have " + train.lastVec().domain().length + " elements");
          trainSamplingFactors = mp._class_sampling_factors.clone(); //clone: don't modify the original
        }
        train = sampleFrameStratified(
            train, train.lastVec(), train.vec(model._output.weightsName()), trainSamplingFactors, (long) (mp._max_after_balance_size * train.numRows()), mp._seed, true, false);
        Vec l = train.lastVec();
        Vec w = train.vec(model._output.weightsName());
        MRUtils.ClassDist cd = new MRUtils.ClassDist(l);
        model._output._modelClassDist = mb._weights != null ? cd.doAll(l, w).rel_dist() : cd.doAll(l).rel_dist();
      }
      model.training_rows = train.numRows();
      if (mb._weights != null && mb._weights.min() == 0 && mb._weights.max() == 1 && mb._weights.isInt()) {
        model.training_rows = Math.round(train.numRows() * mb._weights.mean());
        Log.warn("Not counting " + (train.numRows() - model.training_rows) + " rows with weight=0 towards an epoch.");
      }
      Log.info("One epoch corresponds to " + model.training_rows + " training data rows.");
      trainScoreFrame = sampleFrame(train, mp._score_training_samples, mp._seed); //training scoring dataset is always sampled uniformly from the training dataset
      if (trainScoreFrame != train) Scope.track(trainScoreFrame);

      if (!deepLearning.params()._quiet_mode) Log.info("Number of chunks of the training data: " + train.anyVec().nChunks());
      if (val_fr != null) {
        model.validation_rows = val_fr.numRows();
        // validation scoring dataset can be sampled in multiple ways from the given validation dataset
        if (model._output.isClassifier() && mp._balance_classes && mp._score_validation_sampling == DeepLearningParameters.ClassSamplingMethod.Stratified) {
          deepLearning.job().update(0, "Sampling validation data (stratified)...");
          validScoreFrame = sampleFrameStratified(val_fr, val_fr.lastVec(), val_fr.vec(model._output.weightsName()), null,
              mp._score_validation_samples > 0 ? mp._score_validation_samples : val_fr.numRows(), mp._seed + 1, false /* no oversampling */, false);
        } else {
          deepLearning.job().update(0, "Sampling validation data...");
          validScoreFrame = sampleFrame(val_fr, mp._score_validation_samples, mp._seed + 1);
          if (validScoreFrame != val_fr) Scope.track(validScoreFrame);
        }
        if (!deepLearning.params()._quiet_mode)
          Log.info("Number of chunks of the validation data: " + validScoreFrame.anyVec().nChunks());
      }

      // Set train_samples_per_iteration size (cannot be done earlier since this depends on whether stratified sampling is done)
      model.actual_train_samples_per_iteration = DeepLearningBig.computeTrainSamplesPerIteration(mp, model.training_rows, model);
      // Determine whether shuffling is enforced
      if (mp._replicate_training_data && (model.actual_train_samples_per_iteration == model.training_rows * (mp._single_node_mode ? 1 : H2O.CLOUD.size())) && !mp._shuffle_training_data && H2O.CLOUD.size() > 1 && !mp._reproducible) {
        if (!mp._quiet_mode)
          Log.info("Enabling training data shuffling, because all nodes train on the full dataset (replicated training data).");
        mp._shuffle_training_data = true;
      }
      if (!mp._shuffle_training_data && model.actual_train_samples_per_iteration == model.training_rows && train.anyVec().nChunks() == 1) {
        if (!mp._quiet_mode)
          Log.info("Enabling training data shuffling to avoid training rows in the same order over and over (no Hogwild since there's only 1 chunk).");
        mp._shuffle_training_data = true;
      }

//        if (!mp._quiet_mode) Log.info("Initial model:\n" + model.model_info());
      long now = System.currentTimeMillis();
      model._timeLastIterationEnter = now;
      if (deepLearning.params()._autoencoder) {
        deepLearning.job().update(0, "Scoring null model of autoencoder...");
        if (!mp._quiet_mode)
          Log.info("Scoring the null model of the autoencoder.");
        model.doScoring(trainScoreFrame, validScoreFrame, deepLearning.job()._key, 0, false); //get the null model reconstruction error
      }
      // put the initial version of the model into DKV
      model.update(deepLearning.job());
      model.total_setup_time_ms += now - deepLearning.job().start_time();
      Log.info("Total setup time: " + PrettyPrint.msecs(model.total_setup_time_ms, true));
      Log.info("Starting to train the Deep Learning model.");
      deepLearning.job().update(0, "Training...");

      //main loop
      for (; ; ) {
        model.iterations++;
        model.model_info().data_info.currentData = model._parms.trainData;

        final float syncFraction = rowFraction(train, mp, model);
        model.set_model_info(mp._epochs == 0 ? model.model_info() : H2O.CLOUD.size() > 1 && mp._replicate_training_data ? (mp._single_node_mode ?
            new DeepLearningTask2(deepLearning.job()._key, train, model.model_info(), syncFraction, model.iterations).doAll(Key.make(H2O.SELF)).model_info() : //replicated data + single node mode
            new DeepLearningTask2(deepLearning.job()._key, train, model.model_info(), syncFraction, model.iterations).doAllNodes().model_info()) : //replicated data + multi-node mode
            new DeepLearningTask(deepLearning.job()._key, model.model_info(), syncFraction, model.iterations).doAll(train).model_info()); //distributed data (always in multi-node mode)
        if (mb.stop_requested() && !mb.timeout()) throw new Job.JobCancelledException();
        if (!model.doScoring(trainScoreFrame, validScoreFrame, deepLearning.job()._key, model.iterations, false))
          break; //finished training (or early stopping or convergence)
        if (mb.timeout()) { //stop after scoring
          deepLearning.job().update((long) (mp._epochs * train.numRows())); // mark progress as completed
          break;
        }
      }

      // replace the model with the best model so far (if it's better)
      if (!mb.stop_requested() && deepLearning.params()._overwrite_with_best_model && model.actual_best_model_key != null && deepLearning.params()._nfolds == 0) {
        DeepLearningModel best_model = DKV.getGet(model.actual_best_model_key);
        if (best_model != null && best_model.loss() < model.loss() && Arrays.equals(best_model.model_info().units, model.model_info().units)) {
          if (!deepLearning.params()._quiet_mode) {
            Log.info("Setting the model to be the best model so far (based on scoring history).");
            Log.info("Best model's loss: " + best_model.loss() + " vs this model's loss (before overwriting it with the best model): " + model.loss());
          }
          DeepLearningModelInfo mi = IcedUtils.deepCopy(best_model.model_info());
          // Don't cheat - count full amount of training samples, since that's the amount of training it took to train (without finding anything better)
          mi.set_processed_global(model.model_info().get_processed_global());
          mi.set_processed_local(model.model_info().get_processed_local());
          DeepLearningParameters parms = model.model_info().get_params(); // backup the parameters for this model
          model.set_model_info(mi); // this overwrites also the parameters from the previous best model, but we only want the state
          model.model_info().parameters = parms; // restore the parameters
          model.update(deepLearning.job());
          model.doScoring(trainScoreFrame, validScoreFrame, deepLearning.job()._key, model.iterations, true);
          if (best_model.loss() != model.loss()) {
            if (!deepLearning.params()._quiet_mode) {
              Log.info("Best model's loss: " + best_model.loss() + " vs this model's loss (after overwriting it with the best model) : " + model.loss());
            }
            Log.warn("Even though the model was reset to the previous best model, we observe different scoring results. " +
                "Most likely, the data set has changed during a checkpoint restart. If so, please compare the metrics to observe your data shift.");
          }
        }
      }
      //store coefficient names for future use
      //possibly change 
      model.model_info().data_info().coefNames();
    } finally {
      if (!deepLearning.params()._quiet_mode) {
        Log.info("==============================================================================================================================================================================");
        if (mb.stop_requested()) {
          Log.info("Deep Learning model training was interrupted.");
        } else {
          Log.info("Finished training the Deep Learning model.");
          if (model != null) Log.info(model);
        }
        Log.info("==============================================================================================================================================================================");
      }
      if (model != null) {
        model.deleteElasticAverageModels();
        model.unlock(deepLearning.job());
        if (model.actual_best_model_key != null) {
          assert (model.actual_best_model_key != model._key);
          DKV.remove(model.actual_best_model_key);
        }
      }
    }
    return model;
  }


  /**
   * Compute the fraction of rows that need to be used for training during one iteration
   *
   * @param numRows                     number of training rows
   * @param train_samples_per_iteration number of training rows to be processed per iteration
   * @param replicate_training_data     whether of not the training data is replicated on each node
   * @return fraction of rows to be used for training during one iteration
   */
  private float computeRowUsageFraction(final long numRows, final long train_samples_per_iteration, final boolean replicate_training_data) {
    float rowUsageFraction = (float) train_samples_per_iteration / numRows;
    if (replicate_training_data) rowUsageFraction /= H2O.CLOUD.size();
    assert (rowUsageFraction > 0);
    return rowUsageFraction;
  }

  private float rowFraction(Frame train, DeepLearningParameters p, DeepLearningModel m) {
    return computeRowUsageFraction(train.numRows(), m.actual_train_samples_per_iteration, p._replicate_training_data);
  }
}
