package hex.deepwater;

import hex.DataInfo;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ToEigenVec;
import hex.genmodel.algos.deepwater.DeepwaterMojoModel;
import hex.util.LinearAlgebraUtils;
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

import static hex.deepwater.DeepWaterModel.makeDataInfo;
import static water.util.MRUtils.sampleFrame;
import static water.util.MRUtils.sampleFrameStratified;

/**
 * Deep Learning Neural Net implementation based on MRTask
 */
public class DeepWater extends ModelBuilder<DeepWaterModel,DeepWaterParameters,DeepWaterModelOutput> {

  /** Main constructor from Deep Learning parameters */
  public DeepWater(DeepWaterParameters parms ) {
    super(parms);
    init(false);
  }

  public DeepWater(boolean startup_once ) { super(new DeepWaterParameters(),startup_once); }

  /** Check whether we have any Deep Water native backends available */
  public static boolean haveBackend() {
    for (DeepWaterParameters.Backend b : DeepWaterParameters.Backend.values()) {
      if (DeepwaterMojoModel.createDeepWaterBackend(b.toString()) != null) return true;
    }
    return false;
  }

  static boolean haveBackend(DeepWaterParameters.Backend b) {
    return DeepwaterMojoModel.createDeepWaterBackend(b.toString()) != null;
  }

  @Override public BuilderVisibility builderVisibility() {
     return haveBackend() ? BuilderVisibility.Stable : BuilderVisibility.Experimental;
  }

  /** Types of models we can build with DeepWater  */
  @Override public ModelCategory[] can_build() {
    return new ModelCategory[]{
            ModelCategory.Regression,
            ModelCategory.Binomial,
            ModelCategory.Multinomial,
//            ModelCategory.AutoEncoder
    };
  }

  @Override public boolean haveMojo() { return true; }
  @Override public boolean havePojo() { return false; }

  @Override public ToEigenVec getToEigenVec() { return LinearAlgebraUtils.toEigen; }

  @Override public boolean isSupervised() { return !_parms._autoencoder; }

  @Override protected int nModelsInParallel() { return 1; }

  @Override protected DeepWaterDriver trainModelImpl() { return new DeepWaterDriver(); }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the very large number of arguments in the DL Parameter directly. */
  @Override public void init(boolean expensive) {
    super.init(expensive); //drop constant and ignored columns
    _parms.validate(this, expensive);
    if (expensive && error_count() == 0) checkMemoryFootPrint();
  }

  @Override
  protected  boolean ignoreStringColumns(){ return _parms.guessProblemType() == DeepWaterParameters.ProblemType.dataset; }

  @Override
  public void cv_computeAndSetOptimalParameters(ModelBuilder<DeepWaterModel, DeepWaterParameters, DeepWaterModelOutput>[] cvModelBuilders) {
    _parms._overwrite_with_best_model = false;

    if( _parms._stopping_rounds == 0 && _parms._max_runtime_secs == 0) return; // No exciting changes to stopping conditions
    // Extract stopping conditions from each CV model, and compute the best stopping answer
    _parms._stopping_rounds = 0;
    _parms._max_runtime_secs = 0;
    double sum = 0;
    for( ModelBuilder cvmb : cvModelBuilders )
      sum += ((DeepWaterModel)DKV.getGet(cvmb.dest())).last_scored().epoch_counter;
    _parms._epochs = sum/cvModelBuilders.length;
    if( !_parms._quiet_mode ) {
      warn("_epochs", "Setting optimal _epochs to " + _parms._epochs + " for cross-validation main model based on early stopping of cross-validation models.");
      warn("_stopping_rounds", "Disabling convergence-based early stopping for cross-validation main model.");
      warn("_max_runtime_secs", "Disabling maximum allowed runtime for cross-validation main model.");
    }
  }

  public class DeepWaterDriver extends Driver {
    @Override public void computeImpl() {
      init(true); //this can change the seed if it was set to -1
      long cs = _parms.checksum();
      // Something goes wrong
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(DeepWater.this);
      buildModel();
      //check that _parms isn't changed during DL model training
      long cs2 = _parms.checksum();
      assert(cs == cs2);
    }

    /**
     * Train a Deep Learning model, assumes that all members are populated
     * If checkpoint == null, then start training a new model, otherwise continue from a checkpoint
     */
    final void buildModel() {
      DeepWaterModel cp = null;
      if (_parms._checkpoint == null) {
        cp = new DeepWaterModel(_result,_parms,new DeepWaterModelOutput(DeepWater.this),train(),valid(),nclasses());
      } else {
        final DeepWaterModel previous = DKV.getGet(_parms._checkpoint);
        if (previous == null) throw new IllegalArgumentException("Checkpoint not found.");
        Log.info("Resuming from checkpoint.");
        _job.update(0,"Resuming from checkpoint");

        if( isClassifier() != previous._output.isClassifier() )
          throw new H2OIllegalArgumentException("Response type must be the same as for the checkpointed model.");
        if( isSupervised() != previous._output.isSupervised() )
          throw new H2OIllegalArgumentException("Model type must be the same as for the checkpointed model.");

        //READ ONLY
        DeepWaterParameters.Sanity.checkIfParameterChangeAllowed(previous._parms, _parms);

        DataInfo dinfo = null;
        List<Key> removeMe = new ArrayList();
        try {
          // PUBDEV-2513: Adapt _train and _valid (in-place) to match the frames that were used for the previous model
          // This can add or remove dummy columns (can happen if the dataset is sparse and datasets have different non-const columns)
          for (String st : previous.adaptTestForTrain(_train,true,false)) Log.warn(st);
          for (String st : previous.adaptTestForTrain(_valid,true,false)) Log.warn(st);
          if (previous.model_info()._dataInfo!=null) {
            dinfo = makeDataInfo(_train, _valid, _parms);
            DKV.put(dinfo);
            removeMe.add(dinfo._key);
          }
          cp = new DeepWaterModel(dest(), _parms, previous, dinfo);
          cp.write_lock(_job);

          if (!Arrays.equals(cp._output._names, previous._output._names)) {
            throw new H2OIllegalArgumentException("The columns of the training data must be the same as for the checkpointed model. Check ignored columns (or disable ignore_const_cols).");
          }
          if (!Arrays.deepEquals(cp._output._domains, previous._output._domains)) {
            throw new H2OIllegalArgumentException("Categorical factor levels of the training data must be the same as for the checkpointed model.");
          }
          if (dinfo != null && dinfo.fullN() != previous.model_info()._dataInfo.fullN()) {
            throw new H2OIllegalArgumentException("Total number of predictors is different than for the checkpointed model.");
          }
          if (_parms._epochs <= previous.epoch_counter) {
            throw new H2OIllegalArgumentException("Total number of epochs must be larger than the number of epochs already trained for the checkpointed model (" + previous.epoch_counter + ").");
          }

          // these are the mutable parameters that are to be used by the model (stored in model_info.parameters)
          final DeepWaterParameters actualParms = cp.model_info().get_params(); //actually used parameters for model building (defaults filled in, etc.)
          assert (actualParms != previous.model_info().get_params());
          assert (actualParms != _parms);
          assert (actualParms != previous._parms);

          // Update actualNewP parameters based on what the user wants (cp_modifiable parameters only), was cloned from the previous model so far

          //show the user only the changes in the user-facing parameters
          DeepWaterParameters.Sanity.updateParametersDuringCheckpointRestart(_parms, previous._parms, false /*doIt*/, false /*quiet*/);

          //actually change the parameters in the "insider" version of parameters
          DeepWaterParameters.Sanity.updateParametersDuringCheckpointRestart(_parms /*user-given*/, cp.model_info().get_params() /*model_info.parameters that will be used*/, true /*doIt*/, true /*quiet*/);

          // update/sanitize parameters (in place) to set defaults etc.
          DeepWaterParameters.Sanity.modifyParms(_parms, cp.model_info().get_params(), nclasses());

          Log.info("Continuing training after " + String.format("%.3f", previous.epoch_counter) + " epochs from the checkpointed model.");
          cp.update(_job);
        } catch (H2OIllegalArgumentException ex){
          if (cp != null) {
            cp.unlock(_job);
            cp.delete();
            cp = null;
          }
          throw ex;
        } finally {
          if (cp != null) cp.unlock(_job);
          for (Key k : removeMe) DKV.remove(k);
        }
      }
      trainModel(cp);
    }

    /**
     * Compute the fraction of rows that need to be used for training during one iteration
     * @param numRows number of training rows
     * @param train_samples_per_iteration number of training rows to be processed per iteration
     * @param replicate_training_data whether of not the training data is replicated on each node
     * @return fraction of rows to be used for training during one iteration
     */
    private float computeRowUsageFraction(final long numRows, final long train_samples_per_iteration, final boolean replicate_training_data) {
      float rowUsageFraction = (float)train_samples_per_iteration / numRows;
      if (replicate_training_data) rowUsageFraction /= H2O.CLOUD.size();
      assert(rowUsageFraction > 0);
      return rowUsageFraction;
    }
    private float rowFraction(Frame train, DeepWaterParameters p, DeepWaterModel m) {
      return computeRowUsageFraction(train.numRows(), m.actual_train_samples_per_iteration, p._replicate_training_data);
    }

    /**
     * Train a Deep Learning neural net model
     * @param model Input model (e.g., from initModel(), or from a previous training run)
     * @return Trained model
     */
    public final DeepWaterModel trainModel(DeepWaterModel model) {
      Frame validScoreFrame = null;
      Frame train, trainScoreFrame;
      boolean cache = false;
      try {
//      if (checkpoint == null && !quiet_mode) logStart(); //if checkpoint is given, some Job's params might be uninitialized (but the restarted model's parameters are correct)
        if (model == null) {
          model = DKV.get(dest()).get();
        }
        Log.info("Model category: " + (_parms._autoencoder ? "Auto-Encoder" : isClassifier() ? "Classification" : "Regression"));
        final long model_size = model.model_info().size();
        Log.info("Approximate number of model parameters (weights/biases/aux): " + String.format("%,d", model_size/4)); //Assuming floating point values
        model.write_lock(_job);
        _job.update(0,"Setting up training data...");
        final DeepWaterParameters mp = model.model_info().get_params();

        // temporary frames of the same "name" as the orig _train/_valid (asking the parameter's Key, not the actual frame)
        // Note: don't put into DKV or they would overwrite the _train/_valid frames!
        Frame tra_fr = new Frame(mp._train, _train.names(), _train.vecs());
        Frame val_fr = _valid != null ? new Frame(mp._valid,_valid.names(), _valid.vecs()) : null;

        train = tra_fr;
        if (model._output.isClassifier() && mp._balance_classes) {
          _job.update(0,"Balancing class distribution of training data...");
          float[] trainSamplingFactors = new float[train.lastVec().domain().length]; //leave initialized to 0 -> will be filled up below
          if (mp._class_sampling_factors != null) {
            if (mp._class_sampling_factors.length != train.lastVec().domain().length)
              throw new IllegalArgumentException("class_sampling_factors must have " + train.lastVec().domain().length + " elements");
            trainSamplingFactors = mp._class_sampling_factors.clone(); //clone: don't modify the original
          }
          train = sampleFrameStratified(
              train, train.lastVec(), train.vec(model._output.weightsName()), trainSamplingFactors, (long)(mp._max_after_balance_size*train.numRows()), mp._seed, true, false);
          Vec l = train.lastVec();
          Vec w = train.vec(model._output.weightsName());
          MRUtils.ClassDist cd = new MRUtils.ClassDist(l);
          model._output._modelClassDist = _weights != null ? cd.doAll(l, w).rel_dist() : cd.doAll(l).rel_dist();
        }
        model.training_rows = train.numRows();
        model.actual_train_samples_per_iteration =
            _parms._train_samples_per_iteration > 0 ? _parms._train_samples_per_iteration : //user-given value (>0)
            _parms._train_samples_per_iteration == -2 ? 32*_parms._mini_batch_size :  //automatic (-2) -> start with something small
                _train.numRows(); //otherwise, do one epoch per iteration (-1 or 0)

        if (_weights != null && _weights.min()==0 && _weights.max()==1 && _weights.isInt()) {
          model.training_rows = Math.round(train.numRows()*_weights.mean());
          Log.warn("Not counting " + (train.numRows() - model.training_rows) + " rows with weight=0 towards an epoch.");
        }
        Log.info("One epoch corresponds to " + model.training_rows + " training data rows.");
        trainScoreFrame = sampleFrame(train, mp._score_training_samples, mp._seed); //training scoring dataset is always sampled uniformly from the training dataset
        if( trainScoreFrame != train ) Scope.track(trainScoreFrame);

        if (!_parms._quiet_mode) Log.info("Number of chunks of the training data: " + train.anyVec().nChunks());
        if (val_fr != null) {
          model.validation_rows = val_fr.numRows();
          // validation scoring dataset can be sampled in multiple ways from the given validation dataset
          _job.update(0,"Sampling validation data...");
          validScoreFrame = sampleFrame(val_fr, mp._score_validation_samples, mp._seed +1);
          if( validScoreFrame != val_fr ) Scope.track(validScoreFrame);
          if (!_parms._quiet_mode) Log.info("Number of chunks of the validation data: " + validScoreFrame.anyVec().nChunks());
        }

        // Set train_samples_per_iteration size (cannot be done earlier since this depends on whether stratified sampling is done)
        // Determine whether shuffling is enforced
        if(mp._replicate_training_data && (model.actual_train_samples_per_iteration == model.training_rows*(mp._single_node_mode ?1:H2O.CLOUD.size())) && !mp._shuffle_training_data && H2O.CLOUD.size() > 1) {
          if (!mp._quiet_mode)
            Log.info("Enabling training data shuffling, because all nodes train on the full dataset (replicated training data).");
          mp._shuffle_training_data = true;
        }
        if(!mp._shuffle_training_data && model.actual_train_samples_per_iteration == model.training_rows && train.anyVec()!=null && train.anyVec().nChunks()==1) {
          if (!mp._quiet_mode)
            Log.info("Enabling training data shuffling to avoid training rows in the same order over and over (no Hogwild since there's only 1 chunk).");
          mp._shuffle_training_data = true;
        }

//        if (!mp._quiet_mode) Log.info("Initial model:\n" + model.model_info());
        long now = System.currentTimeMillis();
        model._timeLastIterationEnter = now;
        if (_parms._autoencoder) {
          _job.update(0,"Scoring null model of autoencoder...");
          if (!mp._quiet_mode)
            Log.info("Scoring the null model of the autoencoder.");
          model.doScoring(trainScoreFrame, validScoreFrame, _job._key, 0, false); //get the null model reconstruction error
        }
        // put the initial version of the model into DKV
        model.update(_job);
        model.total_setup_time_ms += now - _job.start_time();
        Log.info("Total setup time: " + PrettyPrint.msecs(model.total_setup_time_ms, true));
        Log.info("Starting to train the Deep Learning model.");
        _job.update(0,"Training...");

        // decide whether to cache
        long bytes;
        if (mp._problem_type == DeepWaterParameters.ProblemType.image) {
          bytes = train.numRows() * model.model_info()._width * model.model_info()._height * model.model_info()._channels * 4;
        } else {
          bytes = train.byteSize();
        }
        cache = mp._cache_data;
        if (cache) {
          if (bytes < H2O.CLOUD.free_mem() / 2) {
            Log.info("Automatically enabling data caching, expecting to require " + PrettyPrint.bytes(bytes) + ".");
          } else {
            Log.info("Automatically disabling data caching, since it would require too much space: " + PrettyPrint.bytes(bytes) + ".");
            mp._cache_data = false;
            cache = false;
          }
        }

        //main loop
        for(;;) {
          if (mp._epochs==0) break;
          model.iterations++;
          model.set_model_info(mp._epochs == 0 ? model.model_info() : H2O.CLOUD.size() > 1 && mp._replicate_training_data ? (mp._single_node_mode ?
                  new DeepWaterTask2(_job._key, train, model.model_info(), rowFraction(train, mp, model), model.iterations).doAll(Key.make(H2O.SELF)).model_info() :  // replicated data + single node mode
                  new DeepWaterTask2(_job._key, train, model.model_info(), rowFraction(train, mp, model), model.iterations).doAllNodes(             ).model_info()):  // replicated data + multi-node mode
                  new DeepWaterTask (model.model_info(), rowFraction(train, mp, model), _job).doAll     (    train    ).model_info());                                // distributed data (always in multi-node mode)
          long before = System.currentTimeMillis();
          if (_parms._export_native_parameters_prefix !=null && !_parms._export_native_parameters_prefix.equals("")) {
            Log.info("Saving model state.");
            model.exportNativeModel(_parms._export_native_parameters_prefix, model.iterations);
          }
          model.time_for_iteration_overhead_ms = System.currentTimeMillis()-before;
          if (stop_requested() && !timeout()) throw new Job.JobCancelledException();
          if (!model.doScoring(trainScoreFrame, validScoreFrame, _job._key, model.iterations, false)) break; //finished training (or early stopping or convergence)
          if (timeout()) { //stop after scoring
            _job.update((long) (mp._epochs * train.numRows())); // mark progress as completed
            break;
          }
        }

        // replace the model with the best model so far (if it's better)
        if (!stop_requested() && _parms._overwrite_with_best_model && model.actual_best_model_key != null && _parms._nfolds == 0) {
          DeepWaterModel best_model = DKV.getGet(model.actual_best_model_key);
          if (best_model != null && best_model.loss() < model.loss() && Arrays.equals(best_model.model_info()._network, model.model_info()._network)) {
            if (!_parms._quiet_mode) {
              Log.info("Setting the model to be the best model so far (based on scoring history).");
              Log.info("Best model's loss: " + best_model.loss() + " vs this model's loss (before overwriting it with the best model): " + model.loss());
            }
            model.model_info().nativeToJava();
            model.removeNativeState(); //remove native state
            DeepWaterModelInfo mi = IcedUtils.deepCopy(best_model.model_info());
            // Don't cheat - count full amount of training samples, since that's the amount of training it took to train (without finding anything better)
            mi.set_processed_global(model.model_info().get_processed_global());
            mi.set_processed_local(model.model_info().get_processed_local());
            model.set_model_info(mi);
            model.update(_job);
            model.doScoring(trainScoreFrame, validScoreFrame, _job._key, model.iterations, true);
            if (!_parms._quiet_mode) {
              Log.info("  Note: best model was at " + (float) best_model.epoch_counter + " (out of " + (float) model.epoch_counter + ") epochs.");
            }
            if (Math.abs(best_model.loss() - model.loss())>=1e-5*Math.abs(model.loss()+best_model.loss())) {
              Log.info("Best model's loss: " + best_model.loss() + " vs this model's loss (after overwriting it with the best model) : " + model.loss());
              Log.warn("Even though the model was reset to the previous best model, we observe different scoring results. " +
                  "Most likely, the data set has changed during a checkpoint restart. If so, please compare the metrics to observe your data shift.");
            }
          }
        }
      }
      finally {
        if (model != null) {
          if (model.model_info() != null && model.model_info()._backend != null)
            model.model_info().nativeToJava();
          if (cache)
            model.cleanUpCache();
          model.removeNativeState();
        }
        if (!_parms._quiet_mode) {
          Log.info("==============================================================================================================================================================================");
          if (stop_requested()) {
            Log.info("Deep Water model training was interrupted.");
          } else {
            Log.info("Finished training the Deep Water model.");
            Log.info(model);
          }
          Log.info("==============================================================================================================================================================================");
        }
        if (model != null) {
          model.unlock(_job);
          if (model.actual_best_model_key != null) {
            assert (model.actual_best_model_key != model._key);
            DKV.remove(model.actual_best_model_key); //don't call model.delete() as many things are shared with the main model
          }
        }
      }
      return model;
    }
  }


}
