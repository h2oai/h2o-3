package hex.deeplearning;

import hex.*;
import hex.deeplearning.DeepLearningModel.DeepLearningModelOutput;
import hex.schemas.DeepLearningV3;
import hex.schemas.ModelBuilderSchema;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.RebalanceDataSet;
import water.fvec.Vec;
import water.init.Linpack;
import water.init.NetworkTest;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MRUtils;
import water.util.PrettyPrint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static water.util.MRUtils.sampleFrame;
import static water.util.MRUtils.sampleFrameStratified;

/**
 * Deep Learning Neural Net implementation based on MRTask
 */
public class DeepLearning extends ModelBuilder<DeepLearningModel,DeepLearningParameters,DeepLearningModelOutput> {
  /**
   * Main constructor from Deep Learning parameters
   * @param parms
   */
  public DeepLearning( DeepLearningParameters parms ) {
    super("DeepLearning", parms);
    init(false);
  }

  /**
   * Types of models we can build with DeepLearning
   * @return
   */
  @Override public ModelCategory[] can_build() {
    return new ModelCategory[]{
            ModelCategory.Regression,
            ModelCategory.Binomial,
            ModelCategory.Multinomial,
            ModelCategory.AutoEncoder
    };
  }
  public ModelBuilderSchema schema() { return new DeepLearningV3(); }
  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Stable; };
  @Override public boolean isSupervised() { return !_parms._autoencoder; }

  /** Start the DeepLearning training Job on an F/J thread.
   * @param work
   * @param restartTimer*/
  @Override protected Job<DeepLearningModel> trainModelImpl(long work, boolean restartTimer) {
    // We look at _train before init(true) is called, so step around that here:
    return start(new DeepLearningDriver(), work, restartTimer);
  }

  @Override
  public long progressUnits() {
    long work = 1;
    if (null != _train)
      work = (long)(_parms._epochs * _train.numRows());
    return Math.max(1, work);
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
    if (expensive && error_count() == 0) checkMemoryFootPrint();
  }

  /**
   * Helper to create the DataInfo object from training/validation frames and the DL parameters
   * @param train Training frame
   * @param valid Validation frame
   * @param parms Model parameters
   * @return
   */
  static DataInfo makeDataInfo(Frame train, Frame valid, DeepLearningParameters parms) {
    double x = 0.782347234;
    boolean identityLink = new Distribution(parms._distribution, parms._tweedie_power).link(x) == x;
    return new DataInfo(
            Key.make(), //dest key
            train,
            valid,
            parms._autoencoder ? 0 : 1, //nResponses
            parms._autoencoder || parms._use_all_factor_levels, //use all FactorLevels for auto-encoder
            parms._autoencoder ? DataInfo.TransformType.NORMALIZE : parms._sparse ? DataInfo.TransformType.DESCALE : DataInfo.TransformType.STANDARDIZE, //transform predictors
            train.lastVec().isCategorical() ? DataInfo.TransformType.NONE : identityLink ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, //transform response for regression with identity link
            parms._missing_values_handling == DeepLearningParameters.MissingValuesHandling.Skip, //whether to skip missing
            false, // do not replace NAs in numeric cols with mean
            true,  // always add a bucket for missing values
            parms._weights_column != null, // observation weights
            parms._offset_column != null,
            parms._fold_column != null
      );
  }

  @Override protected void checkMemoryFootPrint() {
    if (_parms._checkpoint != null) return;
    long p = _train.degreesOfFreedom() - (_parms._autoencoder ? 0 : _train.lastVec().cardinality());
    String[][] dom = _train.domains();
    // hack: add the factor levels for the NAs
    for (int i=0; i<_train.numCols()-(_parms._autoencoder ? 0 : 1); ++i) {
      if (dom[i] != null) {
        p++;
      }
    }
//    assert(makeDataInfo(_train, _valid, _parms).fullN() == p);
    long output = _parms._autoencoder ? p : Math.abs(_train.lastVec().cardinality());
    // weights
    long model_size = p * _parms._hidden[0];
    int layer=1;
    for (; layer < _parms._hidden.length; ++layer)
      model_size += _parms._hidden[layer-1] * _parms._hidden[layer];
    model_size += _parms._hidden[layer-1] * output;

    // biases
    for (layer=0; layer < _parms._hidden.length; ++layer)
      model_size += _parms._hidden[layer];
    model_size += output;

    if (model_size > 1e8) {
      String msg = "Model is too large: " + model_size + " parameters. Try reducing the number of neurons in the hidden layers (or reduce the number of categorical factors).";
      error("_hidden", msg);
      cancel(msg);
    }
  }

  @Override
  public void modifyParmsForCrossValidationSplits(int i, int N, Key<Model> model_id) {
    super.modifyParmsForCrossValidationSplits(i, N, model_id);
    if (_parms._overwrite_with_best_model) {
      if (!_parms._quiet_mode)
        warn("_overwrite_with_best_model",
                "Disabling overwrite_with_best_model for cross-validation split " + (i+1) + "/" + N + ": No early stopping.");
      _parms._overwrite_with_best_model = false;
    }
  }

  @Override
  public void modifyParmsForCrossValidationMainModel(int N, Key<Model>[] cvModelBuilderKeys) {
    super.modifyParmsForCrossValidationMainModel(N, cvModelBuilderKeys);
    if (_parms._overwrite_with_best_model) {
      if (!_parms._quiet_mode)
        warn("_overwrite_with_best_model", "Disabling overwrite_with_best_model for cross-validation main model: No early stopping.");
      _parms._overwrite_with_best_model = false;
    }
    if (cvModelBuilderKeys !=null) {
      if (_parms._stopping_rounds > 0) {
        double[] epochs = new double[cvModelBuilderKeys.length];
        for (int i=0;i<epochs.length;++i) {
          epochs[i] = ((DeepLearningModel)DKV.getGet((((DeepLearning)DKV.getGet(cvModelBuilderKeys[i])).dest()))).last_scored().epoch_counter;
        }
        _parms._epochs = ArrayUtils.sum(epochs)/epochs.length;
        if (!_parms._quiet_mode)
          warn("_epochs", "Setting optimal _epochs to " + _parms._epochs + " for cross-validation main model based on early stopping of cross-validation models.");
        _parms._stopping_rounds = 0;
        if (!_parms._quiet_mode)
          warn("_stopping_rounds", "Disabling convergence-based early stopping for cross-validation main model.");
      }
    }
  }

  public class DeepLearningDriver extends H2O.H2OCountedCompleter<DeepLearningDriver> {
    protected DeepLearningDriver() { super(true); } // bump priority of drivers
    @Override protected void compute2() {
      try {
        long cs = _parms.checksum();
        init(true);
        // Read lock input
        _parms.read_lock_frames(DeepLearning.this);
        // Something goes wrong
        if (error_count() > 0){
          DeepLearning.this.updateValidationMessages();
          throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(DeepLearning.this);
        }
        buildModel();
        if (isRunning()) done();                 // Job done!
        //check that _parms isn't changed during DL model training
        long cs2 = _parms.checksum();
        assert(cs == cs2);
      } catch( Throwable t ) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          t.printStackTrace();
          failed(t);
          throw t;
        }
      } finally {
        updateModelOutput();
        _parms.read_unlock_frames(DeepLearning.this);
      }
      tryComplete();
    }

    Key self() { return _key; }
    /**
     * Train a Deep Learning model, assumes that all members are populated
     * If checkpoint == null, then start training a new model, otherwise continue from a checkpoint
     */
    public final void buildModel() {
      Scope.enter();
      DeepLearningModel cp = null;
      if (_parms._checkpoint == null) {
        cp = new DeepLearningModel(dest(), _parms, new DeepLearningModel.DeepLearningModelOutput(DeepLearning.this), _train, _valid, nclasses());
        cp.model_info().initializeMembers();
      } else {
        final DeepLearningModel previous = DKV.getGet(_parms._checkpoint);
        if (previous == null) throw new IllegalArgumentException("Checkpoint not found.");
        Log.info("Resuming from checkpoint.");
        new ProgressUpdate("Resuming from checkpoint").fork(_progressKey);

        if( isClassifier() != previous._output.isClassifier() )
          throw new H2OIllegalArgumentException("Response type must be the same as for the checkpointed model.");
        if( isSupervised() != previous._output.isSupervised() )
          throw new H2OIllegalArgumentException("Model type must be the same as for the checkpointed model.");

        // check the user-given arguments for consistency
        DeepLearningParameters oldP = previous._parms; //sanitized parameters for checkpointed model
        DeepLearningParameters newP = _parms; //user-given parameters for restart

        DeepLearningParameters oldP2 = (DeepLearningParameters)oldP.clone();
        DeepLearningParameters newP2 = (DeepLearningParameters)newP.clone();
        DeepLearningParameters.Sanity.modifyParms(oldP, oldP2, nclasses()); //sanitize the user-given parameters
        DeepLearningParameters.Sanity.modifyParms(newP, newP2, nclasses()); //sanitize the user-given parameters
        DeepLearningParameters.Sanity.checkpoint(oldP2, newP2);

        DataInfo dinfo;
        try {
          dinfo = makeDataInfo(_train, _valid, _parms);
          DKV.put(dinfo);
          cp = new DeepLearningModel(dest(), _parms, previous, false, dinfo);
          cp.write_lock(self());

          if (!Arrays.equals(cp._output._names, previous._output._names)) {
            throw new H2OIllegalArgumentException("The columns of the training data must be the same as for the checkpointed model. Check ignored columns (or disable ignore_const_cols).");
          }
          if (!Arrays.deepEquals(cp._output._domains, previous._output._domains)) {
            throw new H2OIllegalArgumentException("Categorical factor levels of the training data must be the same as for the checkpointed model.");
          }
          if (dinfo.fullN() != previous.model_info().data_info().fullN()) {
            throw new H2OIllegalArgumentException("Total number of predictors is different than for the checkpointed model.");
          }
          if (_parms._epochs <= previous.epoch_counter) {
            throw new H2OIllegalArgumentException("Total number of epochs must be larger than the number of epochs already trained for the checkpointed model (" + previous.epoch_counter + ").");
          }

          // these are the mutable parameters that are to be used by the model (stored in model_info._parms)
          final DeepLearningParameters actualNewP = cp.model_info().get_params(); //actually used parameters for model building (defaults filled in, etc.)
          assert (actualNewP != previous.model_info().get_params());
          assert (actualNewP != newP);
          assert (actualNewP != oldP);
          DeepLearningParameters.Sanity.update(actualNewP, newP, nclasses());

          Log.info("Continuing training after " + String.format("%.3f", previous.epoch_counter) + " epochs from the checkpointed model.");
          cp.update(self());
        } catch (H2OIllegalArgumentException ex){
          if (cp != null) {
            cp.unlock(self());
            cp.delete();
            cp = null;
          }
          throw ex;
        } finally {
          if (cp != null) cp.unlock(self());
        }
      }
      trainModel(cp);

      // clean up, but don't delete the model and the training/validation model metrics
      List<Key> keep = new ArrayList<>();
      try {
        keep.add(dest());
        keep.add(cp.model_info().data_info()._key);
        // Do not remove training metrics
        keep.add(cp._output._training_metrics._key);
        // And validation model metrics
        if (cp._output._validation_metrics != null) {
          keep.add(cp._output._validation_metrics._key);
        }
        if (cp._output.weights != null && cp._output.biases != null) {
          for (Key k : Arrays.asList(cp._output.weights)) {
            keep.add(k);
            for (Vec vk : ((Frame) DKV.getGet(k)).vecs()) {
              keep.add(vk._key);
            }
          }
          for (Key k : Arrays.asList(cp._output.biases)) {
            keep.add(k);
            for (Vec vk : ((Frame) DKV.getGet(k)).vecs()) {
              keep.add(vk._key);
            }
          }
        }
      } finally {
        Scope.exit(keep.toArray(new Key[0]));
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
//      if (checkpoint == null && !quiet_mode) logStart(); //if checkpoint is given, some Job's params might be uninitialized (but the restarted model's parameters are correct)
        if (model == null) {
          model = DKV.get(dest()).get();
        }
        Log.info("Model category: " + (_parms._autoencoder ? "Auto-Encoder" : isClassifier() ? "Classification" : "Regression"));
        final long model_size = model.model_info().size();
        Log.info("Number of model parameters (weights/biases): " + String.format("%,d", model_size));
        model.write_lock(self());
        new ProgressUpdate("Setting up training data...").fork(_progressKey);
        final DeepLearningParameters mp = model.model_info().get_params();

        // temporary frames of the same "name" as the orig _train/_valid (asking the parameter's Key, not the actual frame)
        // Note: don't put into DKV or they would overwrite the _train/_valid frames!
        Frame tra_fr = new Frame(mp._train, _train.names(), _train.vecs());
        Frame val_fr = _valid != null ? new Frame(mp._valid,_valid.names(), _valid.vecs()) : null;

        train = tra_fr;
        if (mp._force_load_balance) {
          new ProgressUpdate("Load balancing training data...").fork(_progressKey);
          train = reBalance(train, mp._replicate_training_data /*rebalance into only 4*cores per node*/, mp._train.toString() + "." + model._key.toString() + ".temporary.train");
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
                  train, train.lastVec(), train.vec(model._output.weightsName()), trainSamplingFactors, (long)(mp._max_after_balance_size*train.numRows()), mp._seed, true, false);
          Vec l = train.lastVec();
          Vec w = train.vec(model._output.weightsName());
          MRUtils.ClassDist cd = new MRUtils.ClassDist(l);
          model._output._modelClassDist = _weights != null ? cd.doAll(l, w).rel_dist() : cd.doAll(l).rel_dist();
        }
        model.training_rows = train.numRows();
        trainScoreFrame = sampleFrame(train, mp._score_training_samples, mp._seed); //training scoring dataset is always sampled uniformly from the training dataset
        if( trainScoreFrame != train ) _delete_me.add(trainScoreFrame);

        if (!_parms._quiet_mode) Log.info("Number of chunks of the training data: " + train.anyVec().nChunks());
        if (val_fr != null) {
          model.validation_rows = val_fr.numRows();
          // validation scoring dataset can be sampled in multiple ways from the given validation dataset
          if (model._output.isClassifier() && mp._balance_classes && mp._score_validation_sampling == DeepLearningParameters.ClassSamplingMethod.Stratified) {
            new ProgressUpdate("Sampling validation data (stratified)...").fork(_progressKey);
            validScoreFrame = sampleFrameStratified(val_fr, val_fr.lastVec(),  val_fr.vec(model._output.weightsName()), null,
                    mp._score_validation_samples > 0 ? mp._score_validation_samples : val_fr.numRows(), mp._seed +1, false /* no oversampling */, false);
          } else {
            new ProgressUpdate("Sampling validation data...").fork(_progressKey);
            validScoreFrame = sampleFrame(val_fr, mp._score_validation_samples, mp._seed +1);
            if( validScoreFrame != val_fr ) _delete_me.add(validScoreFrame);
          }
          if (mp._force_load_balance) {
            new ProgressUpdate("Balancing class distribution of validation data...").fork(_progressKey);
            validScoreFrame = reBalance(validScoreFrame, false /*always split up globally since scoring should be distributed*/, mp._valid.toString() + "." + model._key.toString() + ".temporary.valid");
          }
          if (!_parms._quiet_mode) Log.info("Number of chunks of the validation data: " + validScoreFrame.anyVec().nChunks());
        }

        // Set train_samples_per_iteration size (cannot be done earlier since this depends on whether stratified sampling is done)
        model.actual_train_samples_per_iteration = computeTrainSamplesPerIteration(mp, train.numRows(), model);
        // Determine whether shuffling is enforced
        if(mp._replicate_training_data && (model.actual_train_samples_per_iteration == train.numRows()*(mp._single_node_mode ?1:H2O.CLOUD.size())) && !mp._shuffle_training_data && H2O.CLOUD.size() > 1 && !mp._reproducible) {
          if (!mp._quiet_mode)
            Log.info("Enabling training data shuffling, because all nodes train on the full dataset (replicated training data).");
          mp._shuffle_training_data = true;
        }
        if(!mp._shuffle_training_data && model.actual_train_samples_per_iteration == train.numRows() && train.anyVec().nChunks()==1) {
          if (!mp._quiet_mode)
            Log.info("Enabling training data shuffling to avoid training rows in the same order over and over (no Hogwild since there's only 1 chunk).");
          mp._shuffle_training_data = true;
        }

//        if (!mp._quiet_mode) Log.info("Initial model:\n" + model.model_info());
        if (_parms._autoencoder) {
          new ProgressUpdate("Scoring null model of autoencoder...").fork(_progressKey);
          if (!mp._quiet_mode)
            Log.info("Scoring the null model of the autoencoder.");
          model.doScoring(trainScoreFrame, validScoreFrame, self(), null, 0, false); //get the null model reconstruction error
        }
        // put the initial version of the model into DKV
        model.update(self());
        model._timeLastIterationEnter = System.currentTimeMillis();
        Log.info("Starting to train the Deep Learning model.");
        new ProgressUpdate("Training...").fork(_progressKey);

        //main loop
        int iteration = 1;
        do {
          model.set_model_info(mp._epochs == 0 ? model.model_info() : H2O.CLOUD.size() > 1 && mp._replicate_training_data ? (mp._single_node_mode ?
                  new DeepLearningTask2(self(), train, model.model_info(), rowFraction(train, mp, model), iteration).doAll(Key.make(H2O.SELF)).model_info() : //replicated data + single node mode
                  new DeepLearningTask2(self(), train, model.model_info(), rowFraction(train, mp, model), iteration).doAllNodes(             ).model_info()): //replicated data + multi-node mode
                  new DeepLearningTask (self(),        model.model_info(), rowFraction(train, mp, model), iteration).doAll     (    train    ).model_info()); //distributed data (always in multi-node mode)
        }
        while (isRunning() && model.doScoring(trainScoreFrame, validScoreFrame, self(), _progressKey, iteration++, false));

        // replace the model with the best model so far (if it's better)
        if (isRunning() && _parms._overwrite_with_best_model && model.actual_best_model_key != null && _parms._nfolds == 0) {
          DeepLearningModel best_model = DKV.getGet(model.actual_best_model_key);
          if (best_model != null && best_model.loss() < model.loss() && Arrays.equals(best_model.model_info().units, model.model_info().units)) {
            if (!_parms._quiet_mode)
              Log.info("Setting the model to be the best model so far (based on scoring history).");
            DeepLearningModelInfo mi = best_model.model_info().deep_clone();
            // Don't cheat - count full amount of training samples, since that's the amount of training it took to train (without finding anything better)
            mi.set_processed_global(model.model_info().get_processed_global());
            mi.set_processed_local(model.model_info().get_processed_local());
            model.set_model_info(mi);
            model.update(self());
            model.doScoring(trainScoreFrame, validScoreFrame, self(), _progressKey, iteration, true);
            assert(best_model.loss() == model.loss());
          }
        }

        if (!_parms._quiet_mode) {
          Log.info("==============================================================================================================================================================================");
          if (isCancelledOrCrashed()) {
            Log.info("Deep Learning model training was interrupted.");
          } else {
            Log.info("Finished training the Deep Learning model (" + (iteration-1) + " Map/Reduce iterations)");
            Log.info(model);
          }
          Log.info("==============================================================================================================================================================================");
        }
      }
      finally {
        if (model != null) {
          model.deleteElasticAverageModels();
          model.unlock(self());
          if (model.actual_best_model_key != null) {
            assert (model.actual_best_model_key != model._key);
            DKV.remove(model.actual_best_model_key);
          }
        }
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
    private Frame reBalance(final Frame fr, boolean local, final String name) {
      int chunks = (int)Math.min( 4 * H2O.NUMCPUS * (local ? 1 : H2O.CLOUD.size()), fr.numRows());
      if (fr.anyVec().nChunks() > chunks && !_parms._reproducible) {
        if (!_parms._quiet_mode)
          Log.info("Dataset already contains " + fr.anyVec().nChunks() + " chunks. No need to rebalance.");
        return fr;
      } else if (_parms._reproducible) {
        if (!_parms._quiet_mode)
          Log.warn("Reproducibility enforced - using only 1 thread - can be slow.");
        chunks = 1;
      }
      if (!_parms._quiet_mode)
        Log.info("ReBalancing dataset into (at least) " + chunks + " chunks.");
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
    private long computeTrainSamplesPerIteration(final DeepLearningParameters mp, final long numRows, final DeepLearningModel model) {
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
        int[] msg_sizes = new int[]{ 1, (int)(model_size*4) == (model_size*4) ? (int)(model_size*4) : Integer.MAX_VALUE };
        double[] microseconds_collective = new double[msg_sizes.length];
        NetworkTest.NetworkTester nt = new NetworkTest.NetworkTester(msg_sizes,null,microseconds_collective,model_size>1e6 ? 1 : 5 /*repeats*/,false,true /*only collectives*/);
        nt.compute2();

        //length of the network traffic queue based on log-tree rollup (2 log(nodes))
        int network_queue_length = mp._single_node_mode || H2O.CLOUD.size() == 1? 1 : 2*(int)Math.floor(Math.log(H2O.CLOUD.size())/Math.log(2));

        // heuristics
        double flops_overhead_per_row = 50;
        if (mp._activation == DeepLearningParameters.Activation.Maxout || mp._activation == DeepLearningParameters.Activation.MaxoutWithDropout) {
          flops_overhead_per_row *= 8;
        } else if (mp._activation == DeepLearningParameters.Activation.Tanh || mp._activation == DeepLearningParameters.Activation.TanhWithDropout) {
          flops_overhead_per_row *= 5;
        }

        // target fraction of comm vs cpu time: 5%
        double fraction = mp._single_node_mode || H2O.CLOUD.size() == 1 ? 1e-3 : mp._target_ratio_comm_to_comp; //one single node mode, there's no model averaging effect, so less need to shorten the M/R iteration

        // estimate the time for communication (network) and training (compute)
        model.time_for_communication_us = (H2O.CLOUD.size() == 1 ? 1e4 /* add 10ms for single-node */ : 1e5 /* add 100ms for multi-node MR overhead */) + network_queue_length * microseconds_collective[1];
        double time_per_row_us  = (flops_overhead_per_row * model_size + 10000 * model.model_info().units[0]) / (total_gflops * 1e9) / H2O.SELF._heartbeat._cpus_allowed * 1e6;

        // compute the optimal number of training rows per iteration
        // fraction := time_comm_us / (time_comm_us + tspi * time_per_row_us)  ==>  tspi = (time_comm_us/fraction - time_comm_us)/time_per_row_us
        tspi = (long)((model.time_for_communication_us / fraction - model.time_for_communication_us)/ time_per_row_us);

        tspi = Math.min(tspi, (mp._single_node_mode ? 1 : H2O.CLOUD.size()) * numRows * 10); //not more than 10x of what train_samples_per_iteration=-1 would do

        // If the number is close to a multiple of epochs, use that -> prettier scoring
        if (tspi > numRows && Math.abs(tspi % numRows)/(double)numRows < 0.2)  tspi = tspi - tspi % numRows;
        tspi = Math.min(tspi, (long)(mp._epochs * numRows / 10)); //limit to number of epochs desired, but at least 10 iterations total
        if (H2O.CLOUD.size() == 1 || mp._single_node_mode) {
          tspi = Math.min(tspi, 10*(int)(1e6/time_per_row_us)); //in single-node mode, only run for at most 10 seconds
        }
        tspi = Math.max(1, tspi); //at least 1 row
        tspi = Math.min(100000*H2O.CLOUD.size(), tspi); //at most 100k rows per node for initial guess - can always relax later on

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
        tspi = Math.max(1, Math.min(tspi, (long) (mp._epochs * numRows)));
      }
      assert(tspi != 0 && tspi != -1 && tspi != -2 && tspi >= 1);
      model.tspiGuess = tspi;
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
    private float rowFraction(Frame train, DeepLearningParameters p, DeepLearningModel m) {
      return computeRowUsageFraction(train.numRows(), m.actual_train_samples_per_iteration, p._replicate_training_data);
    }
  }
}
