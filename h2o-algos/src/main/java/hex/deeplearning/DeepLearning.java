
package hex.deeplearning;

import hex.FrameTask.DataInfo;
import hex.ModelBuilder;
import hex.schemas.DeepLearningV2;
import hex.schemas.ModelBuilderSchema;
import water.*;
import water.api.ValidationAdapter;
import water.fvec.Frame;
import water.fvec.RebalanceDataSet;
import water.fvec.Vec;
import water.util.*;
import water.init.NetworkTest;
import water.init.Linpack;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;

import static hex.deeplearning.DeepLearningModel.prepareDataInfo;
import static water.util.MRUtils.sampleFrame;
import static water.util.MRUtils.sampleFrameStratified;

/**
 * Deep Learning Neural Net implementation based on MRTask
 */
public class DeepLearning extends ModelBuilder<DeepLearningModel,DeepLearningModel.DeepLearningParameters,DeepLearningModel.DeepLearningOutput> {
  public DeepLearning( DeepLearningModel.DeepLearningParameters parms ) {
    super("DeepLearning",parms);
  }

  public ModelBuilderSchema schema() { return new DeepLearningV2(); }

  /** Start the DeepLearning training Job on an F/J thread. */
  @Override public Job<DeepLearningModel> train() {
    return start(new DeepLearningDriver(), (long)(_parms.epochs * _parms._training_frame.<Frame>get().numRows()));
  }

  public class DeepLearningDriver extends H2O.H2OCountedCompleter<DeepLearningDriver> {
    @Override protected void compute2() {
      try {
        buildModel();
//      if (n_folds > 0) CrossValUtils.crossValidate(this);
      } catch( Throwable t ) {
        t.printStackTrace();
        cancel2(t);
        throw t;
      } finally {
        done();                 // Job done!
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

    // the following parameters can only be specified in expert mode
    transient final String [] expert_options = new String[] {
            "use_all_factor_levels",
            "loss",
            "max_w2",
            "score_training_samples",
            "score_validation_samples",
            "initial_weight_distribution",
            "initial_weight_scale",
            "diagnostics",
            "rate_decay",
            "score_duty_cycle",
            "variable_importances",
            "fast_mode",
            "score_validation_sampling",
            "ignore_const_cols",
            "force_load_balance",
            "replicate_training_data",
            "shuffle_training_data",
            "nesterov_accelerated_gradient",
            "classification_stop",
            "regression_stop",
            "quiet_mode",
            "max_confusion_matrix_size",
            "max_hit_ratio_k",
            "hidden_dropout_ratios",
            "single_node_mode",
            "sparse",
            "col_major",
            "autoencoder",
            "average_activation",
            "sparsity_beta",
    };

    // the following parameters can be modified when restarting from a checkpoint
    transient final String [] cp_modifiable = new String[] {
            "expert_mode",
            "seed",
            "epochs",
            "score_interval",
            "train_samples_per_iteration",
            "target_ratio_comm_to_comp",
            "score_duty_cycle",
            "classification_stop",
            "regression_stop",
            "quiet_mode",
            "max_confusion_matrix_size",
            "max_hit_ratio_k",
            "diagnostics",
            "variable_importances",
            "force_load_balance",
            "replicate_training_data",
            "shuffle_training_data",
            "single_node_mode",
            "sparse",
            "col_major",
    };

    /**
     * Train a Deep Learning model, assumes that all members are populated
     * If checkpoint == null, then start training a new model, otherwise continue from a checkpoint
     */
    public final void buildModel() {
      Scope.enter();
      DeepLearningModel cp = null;
      Frame tra_fr = _parms._training_frame.get();
      if (_parms.checkpoint == null) cp = initModel();
      else {
        final DeepLearningModel previous = DKV.get(_parms.checkpoint).get();
        if (previous == null) throw new IllegalArgumentException("Checkpoint not found.");
        Log.info("Resuming from checkpoint.");
        if (_parms.n_folds != 0) {
          throw new UnsupportedOperationException("n_folds must be 0: Cross-validation is not supported during checkpoint restarts.");
        }
        else {
//        ((ValidatedJob)previous.job()).xval_models = null; //remove existing cross-validation keys after checkpoint restart
        }
        if (_parms._training_frame == null || !Arrays.equals(_parms._training_frame._kb, previous.model_info().get_params()._training_frame._kb)) {
          throw new IllegalArgumentException("source must be the same as for the checkpointed model.");
        }
        _parms.autoencoder = previous.model_info().get_params().autoencoder;
        if (!_parms.autoencoder && (_parms.response_column == null || !Arrays.equals(tra_fr.vec(_parms.response_column)._key._kb, tra_fr.vec(previous.model_info().get_params().response_column)._key._kb))) {
          throw new IllegalArgumentException("response_vec must be the same as for the checkpointed model.");
        }
        if (ArrayUtils.difference(_parms.ignored_columns, previous.model_info().get_params().ignored_columns).length != 0
                || ArrayUtils.difference(previous.model_info().get_params().ignored_columns, _parms.ignored_columns).length != 0) {
          _parms.ignored_columns = previous.model_info().get_params().ignored_columns;
          Log.warn("Automatically re-using ignored_cols from the checkpointed model.");
        }
        if ((_parms._validation_frame == null) == (previous._parms._validation_frame != null)
                || (_parms._validation_frame != null && _parms._validation_frame != null && previous._parms._validation_frame != null
                && !Arrays.equals(_parms._validation_frame._kb, previous._parms._validation_frame._kb))) {
          throw new IllegalArgumentException("validation must be the same as for the checkpointed model.");
        }
        if (_parms.classification != previous.model_info().get_params().classification) {
          Log.warn("Automatically switching to " + ((_parms.classification=!_parms.classification) ? "classification" : "regression") + " (same as the checkpointed model).");
        }
        _parms.epochs += previous.epoch_counter; //add new epochs to existing model
        Log.info("Adding " + String.format("%.3f", previous.epoch_counter) + " epochs from the checkpointed model.");
        try {
          final DataInfo dataInfo = prepareDataInfo(_parms);
          cp = new DeepLearningModel(previous, dest(), self(), dataInfo);
          cp.write_lock(self());
          final DeepLearningModel.DeepLearningParameters A = cp.model_info().get_params();
          Object B = _parms;
          for (Field fA : A.getClass().getDeclaredFields()) {
            if (ArrayUtils.contains(cp_modifiable, fA.getName())) {
              if (!_parms.expert_mode && ArrayUtils.contains(expert_options, fA.getName())) continue;
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
          if (A.n_folds != 0) {
            Log.warn("Disabling cross-validation: Not supported when resuming training from a checkpoint.");
            A.n_folds = 0;
          }
          cp.update(self());
        } finally {
          if (cp != null) cp.unlock(self());
        }
      }
      trainModel(cp);

      // clean up
      Frame val_fr = _parms._validation_frame==null ? null : _parms._validation_frame.<Frame>get();
      int validlen = val_fr!= null ? val_fr.vecs().length : 0;
      Key[] keep = new Key[tra_fr.vecs().length+validlen+6];
      //don't delete the training data
      for (int i = 0; i< tra_fr.vecs().length; ++i)
        keep[i] = tra_fr.vecs()[i]._key;
      keep[tra_fr.vecs().length] = _parms._training_frame;
      //don't delete the validation data
      for (int i = 0; i< validlen; ++i)
        keep[i] = val_fr.vecs()[i]._key;
      if (val_fr != null) keep[tra_fr.vecs().length+1] = _parms._validation_frame;
      //don't delete the model
      keep[tra_fr.vecs().length+2] = _dest;
      keep[tra_fr.vecs().length+3] = cp.actual_best_model_key;
      //don't delete the job
      keep[tra_fr.vecs().length+4] = self();
      keep[tra_fr.vecs().length+5] = _progressKey;
      Scope.exit(keep);
    }


    /**
     * Create an initial Deep Learning model, typically to be trained by trainModel(model)
     * @return Randomly initialized model
     */
    public final DeepLearningModel initModel() {
      try {
        lock_data();
        if (_parms.sanityCheckParameters() > 0)
          throw new IllegalArgumentException("Error(s) in model parameters: " + _parms.validationErrors());

        final DataInfo dinfo = prepareDataInfo(_parms);
        final Vec resp = dinfo._adaptedFrame.lastVec(); //convention from DataInfo: response is the last Vec
        float[] priorDist = _parms.classification ? new MRUtils.ClassDist(resp).doAll(resp).rel_dist() : null;
        final DeepLearningModel model = new DeepLearningModel(dest(), self(), _parms._training_frame, dinfo, (DeepLearningModel.DeepLearningParameters)_parms.clone(), priorDist);
        model.model_info().initializeMembers();
        return model;
      }
      finally {
        unlock_data();
      }
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
        lock_data();
//      if (checkpoint == null && !quiet_mode) logStart(); //if checkpoint is given, some Job's params might be uninitialized (but the restarted model's parameters are correct)
        if (model == null) {
          model = DKV.get(dest()).get();
        }
        model.write_lock(self());
        final DeepLearningModel.DeepLearningParameters mp = model._parms;
        Frame tra_fr = _parms._training_frame.get();
        Frame val_fr = _parms._validation_frame == null ? null : _parms._validation_frame.<Frame>get();

        ValidationAdapter validAdapter = new ValidationAdapter(val_fr, _parms.classification);
        validAdapter.prepareValidationWithModel(model);

        final long model_size = model.model_info().size();
        if (!_parms.quiet_mode) Log.info("Number of model parameters (weights/biases): " + String.format("%,d", model_size));
        train = model.model_info().data_info()._adaptedFrame;
        if (mp.force_load_balance) train = reBalance(train, mp.replicate_training_data /*rebalance into only 4*cores per node*/);
        if (mp.classification && mp.balance_classes) {
          float[] trainSamplingFactors = new float[train.lastVec().domain().length]; //leave initialized to 0 -> will be filled up below
          if (mp.class_sampling_factors != null) {
            if (mp.class_sampling_factors.length != train.lastVec().domain().length)
              throw new IllegalArgumentException("class_sampling_factors must have " + train.lastVec().domain().length + " elements");
            trainSamplingFactors = mp.class_sampling_factors.clone(); //clone: don't modify the original
          }

          train = sampleFrameStratified(
                  train, train.lastVec(), trainSamplingFactors, (long)(mp.max_after_balance_size*train.numRows()), mp.seed, true, false);
          model.setModelClassDistribution(new MRUtils.ClassDist(train.lastVec()).doAll(train.lastVec()).rel_dist());
        }
        model.training_rows = train.numRows();
        trainScoreFrame = sampleFrame(train, mp.score_training_samples, mp.seed); //training scoring dataset is always sampled uniformly from the training dataset

        if (!_parms.quiet_mode) Log.info("Number of chunks of the training data: " + train.anyVec().nChunks());
        if (val_fr != null) {
          model.validation_rows = val_fr.numRows();
          Frame adaptedValid = validAdapter.getValidation();
          if (validAdapter.getValidAdaptor().needsAdaptation2CM()) {

            int rIndex = 0;
            for( int i = 0; i < tra_fr.names().length; i++ ) {
              if (tra_fr._names[i] == _parms.response_column) rIndex = i;
            }
            final String responseName = tra_fr._names != null && rIndex >= 0 ? tra_fr._names[rIndex] : "response_vec";
            adaptedValid.add(validAdapter.getValidAdaptor().adaptedValidationResponse(responseName), validAdapter.getValidAdaptor().getAdaptedValidationResponse2CM());
          }
          // validation scoring dataset can be sampled in multiple ways from the given validation dataset
          if (mp.classification && mp.balance_classes && mp.score_validation_sampling == DeepLearningModel.DeepLearningParameters.ClassSamplingMethod.Stratified) {
            validScoreFrame = sampleFrameStratified(adaptedValid, adaptedValid.lastVec(), null,
                    mp.score_validation_samples > 0 ? mp.score_validation_samples : adaptedValid.numRows(), mp.seed+1, false /* no oversampling */, false);
          } else {
            validScoreFrame = sampleFrame(adaptedValid, mp.score_validation_samples, mp.seed+1);
          }
          if (mp.force_load_balance) validScoreFrame = reBalance(validScoreFrame, false /*always split up globally since scoring should be distributed*/);
          if (!_parms.quiet_mode) Log.info("Number of chunks of the validation data: " + validScoreFrame.anyVec().nChunks());
        }

        // Set train_samples_per_iteration size (cannot be done earlier since this depends on whether stratified sampling is done)
        model.actual_train_samples_per_iteration = computeTrainSamplesPerIteration(mp, train.numRows(), model);
        // Determine whether shuffling is enforced
        if(mp.replicate_training_data && (model.actual_train_samples_per_iteration == train.numRows()*(mp.single_node_mode?1:H2O.CLOUD.size())) && !mp.shuffle_training_data && H2O.CLOUD.size() > 1) {
          Log.warn("Enabling training data shuffling, because all nodes train on the full dataset (replicated training data).");
          mp.shuffle_training_data = true;
        }

        model._timeLastScoreEnter = System.currentTimeMillis(); //to keep track of time per iteration, must be called before first call to doScoring

        if (!mp.quiet_mode) Log.info("Initial model:\n" + model.model_info());
        if (_parms.autoencoder) model.doScoring(train, trainScoreFrame, validScoreFrame, self(), validAdapter.getValidAdaptor()); //get the null model reconstruction error
        // put the initial version of the model into DKV
        model.update(self());
        Log.info("Starting to train the Deep Learning model.");

        //main loop
        do {
          model.set_model_info(mp.epochs == 0 ? model.model_info() : H2O.CLOUD.size() > 1 && mp.replicate_training_data ? (mp.single_node_mode ?
                  new DeepLearningTask2(self(), train, model.model_info(), rowFraction(train, mp, model)).doAll(Key.make()).model_info() : //replicated data + single node mode
                  new DeepLearningTask2(self(), train, model.model_info(), rowFraction(train, mp, model)).doAllNodes().model_info()) : //replicated data + multi-node mode
                  new DeepLearningTask(self(), model.model_info(), rowFraction(train, mp, model)).doAll(train).model_info()); //distributed data (always in multi-node mode)
          update(model.actual_train_samples_per_iteration); //update progress
          if (!mp.quiet_mode) Log.info("Progress: " + PrettyPrint.formatPct(progress()));
        }
        while (model.doScoring(train, trainScoreFrame, validScoreFrame, self(), validAdapter.getValidAdaptor()));

        // replace the model with the best model so far (if it's better)
        if (!isCancelledOrCrashed() && _parms.override_with_best_model && model.actual_best_model_key != null && _parms.n_folds == 0) {
          DeepLearningModel best_model = DKV.get(model.actual_best_model_key).get();
          if (best_model != null && best_model.error() < model.error() && Arrays.equals(best_model.model_info().units, model.model_info().units)) {
            Log.info("Setting the model to be the best model so far (based on scoring history).");
            DeepLearningModel.DeepLearningModelInfo mi = best_model.model_info().deep_clone();
            // Don't cheat - count full amount of training samples, since that's the amount of training it took to train (without finding anything better)
            mi.set_processed_global(model.model_info().get_processed_global());
            mi.set_processed_local(model.model_info().get_processed_local());
            model.set_model_info(mi);
            model.update(self());
            model.doScoring(train, trainScoreFrame, validScoreFrame, self(), validAdapter.getValidAdaptor());
            assert(best_model.error() == model.error());
          }
        }

        Log.info(model);
        Log.info("Finished training the Deep Learning model.");
      }
      catch(RuntimeException ex) {
        model = DKV.get(dest()).get();
        _state = JobState.CANCELLED; //for JSON REST response
        Log.info("Deep Learning model building was cancelled.");
        throw ex;
      }
      finally {
        if (model != null) model.unlock(self());
        unlock_data();
        for (Frame f : _delete_me) f.delete(); //delete internally rebalanced frames
      }
      return model;
    }
    transient HashSet<Frame> _delete_me = new HashSet<>();

    /**
     * Lock the input datasets against deletes
     */
    private void lock_data() {
      _parms._training_frame.<Frame>get().read_lock(self());
      if( _parms._validation_frame != null && _parms._training_frame != null && !_parms._training_frame.equals(_parms._validation_frame) )
        _parms._validation_frame.<Frame>get().read_lock(self());
    }

    /**
     * Release the lock for the input datasets
     */
    private void unlock_data() {
      _parms._training_frame.<Frame>get().unlock(self());
      if( _parms._validation_frame != null && _parms._training_frame != null && !_parms._training_frame.equals(_parms._validation_frame) )
        _parms._validation_frame.<Frame>get().unlock(self());
    }

    /**
     * Rebalance a frame for load balancing
     * @param fr Input frame
     * @param local whether to only create enough chunks to max out all cores on one node only
     * @return Frame that has potentially more chunks
     */
    private Frame reBalance(final Frame fr, boolean local) {
      final int chunks = (int)Math.min( 4 * H2O.NUMCPUS * (local ? 1 : H2O.CLOUD.size()), fr.numRows());
      if (fr.anyVec().nChunks() > chunks) {
        Log.info("Dataset already contains " + fr.anyVec().nChunks() + " chunks. No need to rebalance.");
        return fr;
      }
      if (!_parms.quiet_mode) Log.info("ReBalancing dataset into (at least) " + chunks + " chunks.");
//      return MRUtils.shuffleAndBalance(fr, chunks, seed, local, shuffle_training_data);
      Key newKey = fr._key != null ? Key.make(fr._key.toString() + ".balanced") : Key.make();
      newKey = Key.makeUserHidden(newKey);
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
      long tspi = mp.train_samples_per_iteration;
      assert(tspi == 0 || tspi == -1 || tspi == -2 || tspi >= 1);
      if (tspi == 0 || (!mp.replicate_training_data && tspi == -1) ) {
        tspi = numRows;
        if (!mp.quiet_mode) Log.info("Setting train_samples_per_iteration (" + mp.train_samples_per_iteration + ") to one epoch: #rows (" + tspi + ").");
      }
      else if (tspi == -1) {
        tspi = (mp.single_node_mode ? 1 : H2O.CLOUD.size()) * numRows;
        if (!mp.quiet_mode) Log.info("Setting train_samples_per_iteration (" + mp.train_samples_per_iteration + ") to #nodes x #rows (" + tspi + ").");
      } else if (tspi == -2) {
        // automatic tuning based on CPU speed, network speed and model size

      // measure cpu speed
      double total_gflops = 0;
      for (H2ONode h2o : H2O.CLOUD._memary) {
        HeartBeat hb = h2o._heartbeat;
        total_gflops += hb._gflops;
      }
      if (mp.single_node_mode) total_gflops /= H2O.CLOUD.size();
      if (total_gflops == 0) {
        total_gflops = Linpack.run(H2O.SELF._heartbeat._cpus_allowed) * (mp.single_node_mode ? 1 : H2O.CLOUD.size());
      }

      final long model_size = model.model_info().size();
      int[] msg_sizes = new int[]{ (int)(model_size*4) == (model_size*4) ? (int)(model_size*4) : Integer.MAX_VALUE };
      double[] microseconds_collective = new double[msg_sizes.length];
      NetworkTest.NetworkTester nt = new NetworkTest.NetworkTester(msg_sizes,null,microseconds_collective,model_size>1e6 ? 1 : 5 /*repeats*/,false,true /*only collectives*/);
      nt.compute2();

      //length of the network traffic queue based on log-tree rollup (2 log(nodes))
      int network_queue_length = mp.single_node_mode || H2O.CLOUD.size() == 1? 1 : 2*(int)Math.floor(Math.log(H2O.CLOUD.size())/Math.log(2));

      // heuristics
      double flops_overhead_per_row = 30;
      if (mp.activation == DeepLearningModel.DeepLearningParameters.Activation.Maxout || mp.activation == DeepLearningModel.DeepLearningParameters.Activation.MaxoutWithDropout) {
        flops_overhead_per_row *= 8;
      } else if (mp.activation == DeepLearningModel.DeepLearningParameters.Activation.Tanh || mp.activation == DeepLearningModel.DeepLearningParameters.Activation.TanhWithDropout) {
        flops_overhead_per_row *= 5;
      }

      // target fraction of comm vs cpu time: 5%
      double fraction = mp.single_node_mode || H2O.CLOUD.size() == 1 ? 1e-3 : 0.05; //one single node mode, there's no model averaging effect, so less need to shorten the M/R iteration

      // estimate the time for communication (network) and training (compute)
      model.time_for_communication_us = (H2O.CLOUD.size() == 1 ? 1e4 /* add 10ms for single-node */ : 0) + network_queue_length * microseconds_collective[0];
      double time_per_row_us  = flops_overhead_per_row * model_size / (total_gflops * 1e9) / H2O.SELF._heartbeat._cpus_allowed * 1e6;

      // compute the optimal number of training rows per iteration
      // fraction := time_comm_us / (time_comm_us + tspi * time_per_row_us)  ==>  tspi = (time_comm_us/fraction - time_comm_us)/time_per_row_us
      tspi = (long)((model.time_for_communication_us / fraction - model.time_for_communication_us)/ time_per_row_us);

      tspi = Math.min(tspi, (mp.single_node_mode ? 1 : H2O.CLOUD.size()) * numRows * 10); //not more than 10x of what train_samples_per_iteration=-1 would do

      // If the number is close to a multiple of epochs, use that -> prettier scoring
      if (tspi > numRows && Math.abs(tspi % numRows)/(double)numRows < 0.2)  tspi = tspi - tspi % numRows;
      tspi = Math.min(tspi, (long)(mp.epochs * numRows / 10)); //limit to number of epochs desired, but at least 10 iterations total
      tspi = Math.max(1, tspi); //at least 1 point

      if (!mp.quiet_mode) {
        Log.info("Auto-tuning parameter 'train_samples_per_iteration':");
        Log.info("Estimated compute power : " + (int)total_gflops + " GFlops");
        Log.info("Estimated time for comm : " + PrettyPrint.usecs((long) model.time_for_communication_us));
        Log.info("Estimated time per row  : " + ((long)time_per_row_us > 0 ? PrettyPrint.usecs((long) time_per_row_us) : time_per_row_us + " usecs"));
        Log.info("Estimated training speed: " + (int)(1e6/time_per_row_us) + " rows/sec");
        Log.info("Setting train_samples_per_iteration (" + mp.train_samples_per_iteration + ") to auto-tuned value: " + tspi);
      }

      } else {
        // limit user-given value to number of epochs desired
        tspi = Math.min(tspi, (long)(mp.epochs * numRows));
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
      return computeRowUsageFraction(train.numRows(), m.actual_train_samples_per_iteration, p.replicate_training_data);
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
