package hex.deeplearning;


import hex.DataInfo;
import hex.Model;
import hex.SupervisedModelBuilder;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.MissingValuesHandling;
import hex.schemas.DeepLearningV2;
import hex.schemas.ModelBuilderSchema;
import water.*;
import water.fvec.Frame;
import water.fvec.RebalanceDataSet;
import water.init.Linpack;
import water.init.NetworkTest;
import water.util.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;

import static water.util.MRUtils.sampleFrame;
import static water.util.MRUtils.sampleFrameStratified;

/**
 * Deep Learning Neural Net implementation based on MRTask
 */
public class DeepLearning extends SupervisedModelBuilder<DeepLearningModel,DeepLearningModel.DeepLearningParameters,DeepLearningModel.DeepLearningModelOutput> {
  @Override
  public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{
            Model.ModelCategory.Regression,
            Model.ModelCategory.Binomial,
            Model.ModelCategory.Multinomial,
    };
  }

  @Override
  public boolean isSupervised() {
    return !_parms._autoencoder;
  }

  public DeepLearning( DeepLearningModel.DeepLearningParameters parms ) {
    super("DeepLearning",parms); init(false);
  }

  public ModelBuilderSchema schema() { return new DeepLearningV2(); }

  /** Start the DeepLearning training Job on an F/J thread. */
  @Override public Job<DeepLearningModel> trainModel() {
    return start(new DeepLearningDriver(), (long)(_parms._epochs * _train.numRows()));
  }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the very large number of arguments in the DL Parameter directly. */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    _parms.validate(this, expensive);
  }

  public class DeepLearningDriver extends H2O.H2OCountedCompleter<DeepLearningDriver> {
    @Override protected void compute2() {
      try {
        Scope.enter();
        _parms.read_lock_frames(DeepLearning.this);
        init(true);
        if (error_count() > 0)
          throw new IllegalArgumentException("Found validation errors: " + validationErrors());
        buildModel();
        done();                 // Job done!
//      if (n_folds > 0) CrossValUtils.crossValidate(this);
      } catch( Throwable t ) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          failed(t);
          throw t;
        }
      } finally {
        _parms.read_unlock_frames(DeepLearning.this);
        Scope.exit();
      }
      tryComplete();
    }

    Key self() { return _key; }

//  /**
//   * Report the relative progress of building a Deep Learning model (measured by how many epochs are done)
//   * @return floating point number between 0 and 1
//   */
//  @Override public float progress(){
//    if(UKV.get(dest()) == null)return 0;
//    DeepLearningModel m = UKV.get(dest());
//    if (m != null && m.model_info()!=null ) {
//      final float p = (float) Math.min(1, (m.epoch_counter / m.model_info().get_params().epochs));
//      return cv_progress(p);
//    }
//    return 0;
//  }


    // the following parameters can be modified when restarting from a checkpoint
    transient final String [] cp_modifiable = new String[] {
            "_expert_mode",
            "_seed",
            "_epochs",
            "_score_interval",
            "_train_samples_per_iteration",
            "_target_ratio_comm_to_comp",
            "_score_duty_cycle",
            "_classification_stop",
            "_regression_stop",
            "_quiet_mode",
            "_max_confusion_matrix_size",
            "_max_hit_ratio_k",
            "_diagnostics",
            "_variable_importances",
            "_force_load_balance",
            "_replicate_training_data",
            "_shuffle_training_data",
            "_single_node_mode",
            "_sparse",
            "_col_major",
            // Allow modification of the regularization parameters after a checkpoint restart
            "_l1",
            "_l2",
            "_max_w2",
    };

    /**
     * Train a Deep Learning model, assumes that all members are populated
     * If checkpoint == null, then start training a new model, otherwise continue from a checkpoint
     */
    public final void buildModel() {
      Scope.enter();
      DeepLearningModel cp = null;
      if (_parms._checkpoint == null) {
        cp = new DeepLearningModel(dest(), _parms, new DeepLearningModel.DeepLearningModelOutput(DeepLearning.this), _train, _valid);
        cp.model_info().initializeMembers();
      } else {
        final DeepLearningModel previous = DKV.getGet(_parms._checkpoint);
        if (previous == null) throw new IllegalArgumentException("Checkpoint not found.");
        Log.info("Resuming from checkpoint.");
        new ProgressUpdate("Resuming from checkpoint").fork(_progressKey);
        if (_parms._n_folds != 0)
          throw new UnsupportedOperationException("n_folds must be 0: Cross-validation is not supported during checkpoint restarts.");
        _parms._autoencoder = previous.model_info().get_params()._autoencoder;
        if (!_parms._autoencoder && (_parms._response_column == null || !_parms._response_column.equals(previous.model_info().get_params()._response_column))) {
          throw new IllegalArgumentException("response_vec must be the same as for the checkpointed model.");
        }
        if (ArrayUtils.difference(_parms._ignored_columns, previous.model_info().get_params()._ignored_columns).length != 0
                || ArrayUtils.difference(previous.model_info().get_params()._ignored_columns, _parms._ignored_columns).length != 0) {
          _parms._ignored_columns = previous.model_info().get_params()._ignored_columns;
          Log.warn("Automatically re-using ignored_cols from the checkpointed model.");
        }
        if ((_parms._valid == null) != (previous._parms._valid == null)
                || (_parms._valid != null  && !_parms._valid.equals(previous._parms._valid))) {
          throw new IllegalArgumentException("validation must be the same as for the checkpointed model.");
        }
        if( isClassifier() != previous._output.isClassifier() )
          Log.warn("Automatically switching to " + (isClassifier() ? "regression" : "classification") + " (same as the checkpointed model).");
        _parms._epochs += previous.epoch_counter; //add new epochs to existing model
        Log.info("Adding " + String.format("%.3f", previous.epoch_counter) + " epochs from the checkpointed model.");

        try {
          final DataInfo dinfo = new DataInfo(Key.make(), _train, _valid,
                                              _parms._autoencoder ? 0 : 1, 
                                              _parms._autoencoder || _parms._use_all_factor_levels, //use all FactorLevels for auto-encoder
                                              _parms._autoencoder ? DataInfo.TransformType.NORMALIZE : DataInfo.TransformType.STANDARDIZE, //transform predictors
                                              isClassifier()     ? DataInfo.TransformType.NONE      : DataInfo.TransformType.STANDARDIZE, _parms._missing_values_handling == MissingValuesHandling.Skip);
          DKV.put(dinfo._key,dinfo);
          cp = new DeepLearningModel(dest(), previous, false, dinfo);
          cp.write_lock(self());
          final DeepLearningModel.DeepLearningParameters A = cp.model_info().get_params();
          Object B = _parms;
          for (Field fA : A.getClass().getDeclaredFields()) {
            if (ArrayUtils.contains(cp_modifiable, fA.getName())) {
//              if (!_parms._expert_mode && ArrayUtils.contains(cp.get_params().expert_options, fA.getName())) continue;
              for (Field fB : B.getClass().getDeclaredFields()) {
                if (fA.equals(fB)) {
                  try {
                    if (fB.get(B) == null || fA.get(A) == null || !fA.get(A).toString().equals(fB.get(B).toString())) { // if either of the two parameters is null, skip the toString()
                      if (fA.get(A) == null && fB.get(B) == null) continue; //if both parameters are null, we don't need to do anything
                      Log.info("Applying user-requested modification of '" + fA.getName() + "': " + fA.get(A) + " -> " + fB.get(B));
                      fA.set(A, fB.get(B));
                    }
                  } catch (IllegalAccessException e) {
                    e.printStackTrace();
                  }
                }
              }
            }
          }
          if (A._n_folds != 0) {
            Log.warn("Disabling cross-validation: Not supported when resuming training from a checkpoint.");
            A._n_folds = 0;
          }
          cp.update(self());
        } finally {
          if (cp != null) cp.unlock(self());
        }
      }
      trainModel(cp);

      // clean up, but don't delete the model and the (last) model metrics
      Key[] mms = cp._output._model_metrics;
      Scope.exit(dest(),mms.length==0 ? null : mms[mms.length-1]);
    }


    /**
     * Train a Deep Learning neural net model
     * @param model Input model (e.g., from initModel(), or from a previous training run)
     * @return Trained model
     */
    public final DeepLearningModel trainModel(DeepLearningModel model) {
      Frame validScoreFrame = null;
      Frame train, trainScoreFrame;
      try {
//      if (checkpoint == null && !quiet_mode) logStart(); //if checkpoint is given, some Job's params might be uninitialized (but the restarted model's parameters are correct)
        if (model == null) {
          model = DKV.get(dest()).get();
        }
        new ProgressUpdate("Setting up training data...").fork(_progressKey);
        model.write_lock(self());
        final DeepLearningModel.DeepLearningParameters mp = model._parms;
        Frame tra_fr = new Frame(mp.train()._key, _train.names(), _train.vecs());
        Frame val_fr = _valid != null ? new Frame(mp.valid()._key, _valid.names(), _valid.vecs()) : null;

        final long model_size = model.model_info().size();
        if (!_parms._quiet_mode) Log.info("Number of model parameters (weights/biases): " + String.format("%,d", model_size));
        train = tra_fr;
        if (mp._force_load_balance) {
          new ProgressUpdate("Load balancing training data...").fork(_progressKey);
          train = reBalance(train, mp._replicate_training_data /*rebalance into only 4*cores per node*/, mp._train.toString() + "." + model._key.toString() + ".train");
        }
        if (model._output.isClassifier() && mp._balance_classes) {
          new ProgressUpdate("Balancing class distribution of training data...").fork(_progressKey);
          float[] trainSamplingFactors = new float[train.lastVec().domain().length]; //leave initialized to 0 -> will be filled up below
          if (mp._class_sampling_factors != null) {
            if (mp._class_sampling_factors.length != train.lastVec().domain().length)
              throw new IllegalArgumentException("class_sampling_factors must have " + train.lastVec().domain().length + " elements");
            trainSamplingFactors = mp._class_sampling_factors.clone(); //clone: don't modify the original
          }
          train = sampleFrameStratified(
                  train, train.lastVec(), trainSamplingFactors, (long)(mp._max_after_balance_size*train.numRows()), mp._seed, true, false);
          model._output._modelClassDist = new MRUtils.ClassDist(train.lastVec()).doAll(train.lastVec()).rel_dist();
        }
        model._output.autoencoder = _parms._autoencoder;
        model.training_rows = train.numRows();
        trainScoreFrame = sampleFrame(train, mp._score_training_samples, mp._seed); //training scoring dataset is always sampled uniformly from the training dataset

        if (!_parms._quiet_mode) Log.info("Number of chunks of the training data: " + train.anyVec().nChunks());
        if (val_fr != null) {
          model.validation_rows = val_fr.numRows();
          // validation scoring dataset can be sampled in multiple ways from the given validation dataset
          if (model._output.isClassifier() && mp._balance_classes && mp._score_validation_sampling == DeepLearningModel.DeepLearningParameters.ClassSamplingMethod.Stratified) {
            new ProgressUpdate("Sampling validation data (stratified)...").fork(_progressKey);
            validScoreFrame = sampleFrameStratified(val_fr, val_fr.lastVec(), null,
                    mp._score_validation_samples > 0 ? mp._score_validation_samples : val_fr.numRows(), mp._seed +1, false /* no oversampling */, false);
          } else {
            new ProgressUpdate("Sampling validation data...").fork(_progressKey);
            validScoreFrame = sampleFrame(val_fr, mp._score_validation_samples, mp._seed +1);
          }
          if (mp._force_load_balance) {
            new ProgressUpdate("Balancing class distribution of validation data...").fork(_progressKey);
            validScoreFrame = reBalance(validScoreFrame, false /*always split up globally since scoring should be distributed*/, mp._valid.toString() + "." + model._key.toString() + ".valid");
          }
          if (!_parms._quiet_mode) Log.info("Number of chunks of the validation data: " + validScoreFrame.anyVec().nChunks());
        }

        // Set train_samples_per_iteration size (cannot be done earlier since this depends on whether stratified sampling is done)
        model.actual_train_samples_per_iteration = computeTrainSamplesPerIteration(mp, train.numRows(), model);
        // Determine whether shuffling is enforced
        if(mp._replicate_training_data && (model.actual_train_samples_per_iteration == train.numRows()*(mp._single_node_mode ?1:H2O.CLOUD.size())) && !mp._shuffle_training_data && H2O.CLOUD.size() > 1 && !mp._reproducible) {
          Log.warn("Enabling training data shuffling, because all nodes train on the full dataset (replicated training data).");
          mp._shuffle_training_data = true;
        }

        model._timeLastScoreEnter = System.currentTimeMillis(); //to keep track of time per iteration, must be called before first call to doScoring

        if (!mp._quiet_mode) Log.info("Initial model:\n" + model.model_info());
        if (_parms._autoencoder) {
          new ProgressUpdate("Scoring null model of autoencoder...").fork(_progressKey);
          model.doScoring(trainScoreFrame, validScoreFrame, self(), null); //get the null model reconstruction error
        }
        // put the initial version of the model into DKV
        model.update(self());
        Log.info("Starting to train the Deep Learning model.");

        //main loop
        do {
          final String speed = (model.run_time!=0 ? (" at " + model.model_info().get_processed_total() * 1000 / model.run_time + " samples/s..."): "...");
          final String etl = model.run_time == 0 ? "" : " Estimated time left: " + PrettyPrint.msecs((long)(model.run_time*(1.-progress())/progress()), true);
          new ProgressUpdate("Training" + speed + etl).fork(_progressKey);
          model.set_model_info(mp._epochs == 0 ? model.model_info() : H2O.CLOUD.size() > 1 && mp._replicate_training_data ? (mp._single_node_mode ?
                  new DeepLearningTask2(self(), train, model.model_info(), rowFraction(train, mp, model)).doAll(Key.make()).model_info() : //replicated data + single node mode
                  new DeepLearningTask2(self(), train, model.model_info(), rowFraction(train, mp, model)).doAllNodes().model_info()) : //replicated data + multi-node mode
                  new DeepLearningTask(self(), model.model_info(), rowFraction(train, mp, model)).doAll(train).model_info()); //distributed data (always in multi-node mode)
          update(model.actual_train_samples_per_iteration); //update progress
        }
        while (model.doScoring(trainScoreFrame, validScoreFrame, self(), _progressKey));

        // replace the model with the best model so far (if it's better)
        if (!isCancelledOrCrashed() && _parms._override_with_best_model && model.actual_best_model_key != null && _parms._n_folds == 0) {
          DeepLearningModel best_model = DKV.getGet(model.actual_best_model_key);
          if (best_model != null && best_model.error() < model.error() && Arrays.equals(best_model.model_info().units, model.model_info().units)) {
            Log.info("Setting the model to be the best model so far (based on scoring history).");
            DeepLearningModel.DeepLearningModelInfo mi = best_model.model_info().deep_clone();
            // Don't cheat - count full amount of training samples, since that's the amount of training it took to train (without finding anything better)
            mi.set_processed_global(model.model_info().get_processed_global());
            mi.set_processed_local(model.model_info().get_processed_local());
            model.set_model_info(mi);
            model.update(self());
            model.doScoring(trainScoreFrame, validScoreFrame, self(), _progressKey);
            assert(best_model.error() == model.error());
          }
        }

        Log.info("==============================================================================================");
        Log.info("Finished training the Deep Learning model.");
        Log.info(model);
        Log.info("==============================================================================================");
      }
      catch(RuntimeException ex) {
        model = DKV.get(dest()).get();
        _state = JobState.CANCELLED; //for JSON REST response
        Log.info("Deep Learning model building was cancelled.");
        throw ex;
      }
      finally {
        if (model != null) model.unlock(self());
        for (Frame f : _delete_me) f.delete(); //delete internally rebalanced frames
      }
      return model;
    }
    transient HashSet<Frame> _delete_me = new HashSet<>();

    /**
     * Rebalance a frame for load balancing
     * @param fr Input frame
     * @param local whether to only create enough chunks to max out all cores on one node only
     * @return Frame that has potentially more chunks
     */
    private Frame reBalance(final Frame fr, boolean local, String name) {
      int chunks = (int)Math.min( 4 * H2O.NUMCPUS * (local ? 1 : H2O.CLOUD.size()), fr.numRows());
      if (fr.anyVec().nChunks() > chunks && !_parms._reproducible) {
        Log.info("Dataset already contains " + fr.anyVec().nChunks() + " chunks. No need to rebalance.");
        return fr;
      } else if (_parms._reproducible) {
        Log.warn("Reproducibility enforced - using only 1 thread - can be slow.");
        chunks = 1;
      }
      if (!_parms._quiet_mode) Log.info("ReBalancing dataset into (at least) " + chunks + " chunks.");
      Key newKey = Key.make(name + ".chunks" + chunks);
      RebalanceDataSet rb = new RebalanceDataSet(fr, newKey, chunks);
      H2O.submitTask(rb);
      rb.join();
      Frame f = DKV.get(newKey).get();
      _delete_me.add(f);
      return f;
    }

    /**
     * Compute the actual train_samples_per_iteration size from the user-given parameter
     * @param mp Model parameter (DeepLearning object)
     * @param numRows number of training rows
     * @param model DL model
     * @return The total number of training rows to be processed per iteration (summed over on all nodes)
     */
    private long computeTrainSamplesPerIteration(final DeepLearningModel.DeepLearningParameters mp, final long numRows, DeepLearningModel model) {
      long tspi = mp._train_samples_per_iteration;
      assert(tspi == 0 || tspi == -1 || tspi == -2 || tspi >= 1);
      if (tspi == 0 || (!mp._replicate_training_data && tspi == -1) ) {
        tspi = numRows;
        if (!mp._quiet_mode) Log.info("Setting train_samples_per_iteration (" + mp._train_samples_per_iteration + ") to one epoch: #rows (" + tspi + ").");
      }
      else if (tspi == -1) {
        tspi = (mp._single_node_mode ? 1 : H2O.CLOUD.size()) * numRows;
        if (!mp._quiet_mode) Log.info("Setting train_samples_per_iteration (" + mp._train_samples_per_iteration + ") to #nodes x #rows (" + tspi + ").");
      } else if (tspi == -2) {
        // automatic tuning based on CPU speed, network speed and model size

      // measure cpu speed
      double total_gflops = 0;
      for (H2ONode h2o : H2O.CLOUD._memary) {
        HeartBeat hb = h2o._heartbeat;
        total_gflops += hb._gflops;
      }
      if (mp._single_node_mode) total_gflops /= H2O.CLOUD.size();
      if (total_gflops == 0) {
        total_gflops = Linpack.run(H2O.SELF._heartbeat._cpus_allowed) * (mp._single_node_mode ? 1 : H2O.CLOUD.size());
      }

      final long model_size = model.model_info().size();
      int[] msg_sizes = new int[]{ (int)(model_size*4) == (model_size*4) ? (int)(model_size*4) : Integer.MAX_VALUE };
      double[] microseconds_collective = new double[msg_sizes.length];
      NetworkTest.NetworkTester nt = new NetworkTest.NetworkTester(msg_sizes,null,microseconds_collective,model_size>1e6 ? 1 : 5 /*repeats*/,false,true /*only collectives*/);
      nt.compute2();

      //length of the network traffic queue based on log-tree rollup (2 log(nodes))
      int network_queue_length = mp._single_node_mode || H2O.CLOUD.size() == 1? 1 : 2*(int)Math.floor(Math.log(H2O.CLOUD.size())/Math.log(2));

      // heuristics
      double flops_overhead_per_row = 30;
      if (mp._activation == DeepLearningModel.DeepLearningParameters.Activation.Maxout || mp._activation == DeepLearningModel.DeepLearningParameters.Activation.MaxoutWithDropout) {
        flops_overhead_per_row *= 8;
      } else if (mp._activation == DeepLearningModel.DeepLearningParameters.Activation.Tanh || mp._activation == DeepLearningModel.DeepLearningParameters.Activation.TanhWithDropout) {
        flops_overhead_per_row *= 5;
      }

      // target fraction of comm vs cpu time: 5%
      double fraction = mp._single_node_mode || H2O.CLOUD.size() == 1 ? 1e-3 : 0.05; //one single node mode, there's no model averaging effect, so less need to shorten the M/R iteration

      // estimate the time for communication (network) and training (compute)
      model.time_for_communication_us = (H2O.CLOUD.size() == 1 ? 1e4 /* add 10ms for single-node */ : 0) + network_queue_length * microseconds_collective[0];
      double time_per_row_us  = flops_overhead_per_row * model_size / (total_gflops * 1e9) / H2O.SELF._heartbeat._cpus_allowed * 1e6;

      // compute the optimal number of training rows per iteration
      // fraction := time_comm_us / (time_comm_us + tspi * time_per_row_us)  ==>  tspi = (time_comm_us/fraction - time_comm_us)/time_per_row_us
      tspi = (long)((model.time_for_communication_us / fraction - model.time_for_communication_us)/ time_per_row_us);

      tspi = Math.min(tspi, (mp._single_node_mode ? 1 : H2O.CLOUD.size()) * numRows * 10); //not more than 10x of what train_samples_per_iteration=-1 would do

      // If the number is close to a multiple of epochs, use that -> prettier scoring
      if (tspi > numRows && Math.abs(tspi % numRows)/(double)numRows < 0.2)  tspi = tspi - tspi % numRows;
      tspi = Math.min(tspi, (long)(mp._epochs * numRows / 10)); //limit to number of epochs desired, but at least 10 iterations total
      tspi = Math.max(1, tspi); //at least 1 point

      if (!mp._quiet_mode) {
        Log.info("Auto-tuning parameter 'train_samples_per_iteration':");
        Log.info("Estimated compute power : " + (int)total_gflops + " GFlops");
        Log.info("Estimated time for comm : " + PrettyPrint.usecs((long) model.time_for_communication_us));
        Log.info("Estimated time per row  : " + ((long)time_per_row_us > 0 ? PrettyPrint.usecs((long) time_per_row_us) : time_per_row_us + " usecs"));
        Log.info("Estimated training speed: " + (int)(1e6/time_per_row_us) + " rows/sec");
        Log.info("Setting train_samples_per_iteration (" + mp._train_samples_per_iteration + ") to auto-tuned value: " + tspi);
      }

      } else {
        // limit user-given value to number of epochs desired
        tspi = Math.min(tspi, (long)(mp._epochs * numRows));
      }
      assert(tspi != 0 && tspi != -1 && tspi != -2 && tspi >= 1);
      return tspi;
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
    private float rowFraction(Frame train, DeepLearningModel.DeepLearningParameters p, DeepLearningModel m) {
      return computeRowUsageFraction(train.numRows(), m.actual_train_samples_per_iteration, p._replicate_training_data);
    }

//  /**
//   * Cross-Validate a DeepLearning model by building new models on N train/test holdout splits
//   * @param splits Frames containing train/test splits
//   * @param cv_preds Array of Frames to store the predictions for each cross-validation run
//   * @param offsets Array to store the offsets of starting row indices for each cross-validation run
//   * @param i Which fold of cross-validation to perform
//   */
//  @Override public void crossValidate(Frame[] splits, Frame[] cv_preds, long[] offsets, int i) {
//    // Train a clone with slightly modified parameters (to account for cross-validation)
//    final DeepLearning cv = (DeepLearning) this.clone();
//    cv.genericCrossValidation(splits, offsets, i);
//    cv_preds[i] = ((DeepLearningModel) UKV.get(cv.dest())).score(cv.validation);
//    new TAtomic<DeepLearningModel>() {
//      @Override public DeepLearningModel atomic(DeepLearningModel m) {
//        if (!keep_cross_validation_splits && /*paranoid*/cv.dest().toString().contains("xval")) {
//          m.get_params().source = null;
//          m.get_params().validation=null;
//          m.get_params().response=null;
//        }
//        return m;
//      }
//    }.invoke(cv.dest());
//  }
  }
}
