package hex.deeplearning;

import hex.*;
import hex.deeplearning.DeepLearningParameters.MissingValuesHandling;
import hex.glm.GLMTask;
import hex.util.LinearAlgebraUtils;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.RebalanceDataSet;
import water.init.Linpack;
import water.init.NetworkTest;
import water.util.Log;
import water.util.PrettyPrint;

import static water.util.MRUtils.sampleFrameStratified;

/**
 * Deep Learning Neural Net implementation based on MRTask
 */
public class DeepLearningBig extends ModelBuilder<DeepLearningModel,DeepLearningParameters,DeepLearningModelOutput> implements DeepLearning<DeepLearningModel> {
  /** Main constructor from Deep Learning parameters */
  public DeepLearningBig(DeepLearningParameters parms ) { super(parms); init(false); }
  public DeepLearningBig(DeepLearningParameters parms, Key<DeepLearningModel> key ) { super(parms,key); init(false); }
  public DeepLearningBig(boolean startup_once ) { super(new DeepLearningParameters(),startup_once); }

  /** Types of models we can build with DeepLearning  */
  @Override public ModelCategory[] can_build() {
    return new ModelCategory[]{
            ModelCategory.Regression,
            ModelCategory.Binomial,
            ModelCategory.Multinomial,
            ModelCategory.AutoEncoder
    };
  }

  @Override public boolean havePojo() { return true; }
  @Override public boolean haveMojo() { return false; }

  @Override
  public ToEigenVec getToEigenVec() {
    return LinearAlgebraUtils.toEigen;
  }

  @Override public boolean isSupervised() { return !_parms._autoencoder; }

  @Override protected int nModelsInParallel() {
    if (!_parms._parallelize_cross_validation || _parms._max_runtime_secs != 0) return 1; //user demands serial building (or we need to honor the time constraints for all CV models equally)
    if (train().byteSize() < 1e6) return _parms._nfolds; //for small data, parallelize over CV models
    return 1;
  }

  @Override
  public void checkMyConditions() {

//    if (_train != null && _train.lastVecName().equals("target")) {
//      System.out.println("******"  + _train + _train.name(0) + ".." + _train.lastVecName() + " size=" + _train.names().length);
//      Thread.dumpStack();
//      throw new UnsupportedOperationException("FUCK YOU " + _train + _train.name(0) + ".." + _train.lastVecName());
//    }
  }
  
  @Override public DeepLearningDriver trainModelImpl() {
    __("trainModelImpl1");
    
    DeepLearningDriver dld = new DeepLearningDriver(this);
    __("trainModelImpl2");
    return dld;
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
   * @param nClasses Number of response levels (1: regression, >=2: classification)
   * @return DataInfo
   */
  public DataInfo makeDataInfo(Frame train, Frame valid, DeepLearningParameters parms, int nClasses) {
    double x = 0.782347234;
    boolean identityLink = new Distribution(parms).link(x) == x;

    boolean haveTargetData = parms.trainData != null && parms.trainData.isCategorical();

    boolean haveTestData = parms.testData != null && parms.testData.isCategorical();

    final DataInfo.TransformType response_transform = 
        haveTestData || !parms._standardize || train.lastVec().isCategorical() ? DataInfo.TransformType.NONE : 
        identityLink ? DataInfo.TransformType.STANDARDIZE : 
                       DataInfo.TransformType.NONE;

    final DataInfo.TransformType predictor_transform = 
        haveTargetData ? DataInfo.TransformType.NONE : 
        parms._standardize ? (parms._autoencoder ? DataInfo.TransformType.NORMALIZE : 
                              parms._sparse      ? DataInfo.TransformType.DESCALE : 
                                                   DataInfo.TransformType.STANDARDIZE) : 
                         DataInfo.TransformType.NONE;
    
    DataInfo dinfo = new DataInfo(
            train,
            valid,
            parms._autoencoder ? 0 : 1, //nResponses
            parms._autoencoder || parms._use_all_factor_levels, //use all FactorLevels for auto-encoder
        predictor_transform, //transform predictors
        response_transform, //transform response for regression with identity link
            parms._missing_values_handling == DeepLearningParameters.MissingValuesHandling.Skip, //whether to skip missing
            false, // do not replace NAs in numeric cols with mean
            true,  // always add a bucket for missing values
            parms._weights_column != null, // observation weights
            parms._offset_column != null,
            parms._fold_column != null
    );

    dinfo.trainData = parms.trainData;
    dinfo.validationData = parms.testData;

    // Checks and adjustments:
    // 1) observation weights (adjust mean/sigmas for predictors and response)
    // 2) NAs (check that there's enough rows left)
    GLMTask.YMUTask ymt = new GLMTask.YMUTask(dinfo, nClasses,!parms._autoencoder && nClasses == 1, parms._missing_values_handling == MissingValuesHandling.Skip, !parms._autoencoder).doAll(dinfo._adaptedFrame);
    if (ymt.wsum() == 0 && parms._missing_values_handling == DeepLearningParameters.MissingValuesHandling.Skip)
      throw new H2OIllegalArgumentException("No rows left in the dataset after filtering out rows with missing values. Ignore columns with many NAs or set missing_values_handling to 'MeanImputation'.");
    if (parms._weights_column != null && parms._offset_column != null) {
      Log.warn("Combination of offset and weights can lead to slight differences because Rollupstats aren't weighted - need to re-calculate weighted mean/sigma of the response including offset terms.");
    }
    if (parms._weights_column != null && parms._offset_column == null /*FIXME: offset not yet implemented*/) {
      dinfo.updateWeightedSigmaAndMean(ymt.predictorSDs(), ymt.predictorMeans());
      if (nClasses == 1)
        dinfo.updateWeightedSigmaAndMeanForResponse(ymt.responseSDs(), ymt.responseMeans());
    }
    return dinfo;
  }

  @Override protected void checkMemoryFootPrint() {
    if (_parms._checkpoint != null) return;
    long p = hex.util.LinearAlgebraUtils.numColsExp(train(),true) - (_parms._autoencoder ? 0 : train().lastVec().cardinality());
    String[][] dom = train().domains();
    // hack: add the factor levels for the NAs
    for (int i=0; i<train().numCols()-(_parms._autoencoder ? 0 : 1); ++i) {
      if (dom[i] != null) {
        p++;
      }
    }
//    assert(makeDataInfo(_train, _valid, _parms).fullN() == p);
    long output = _parms._autoencoder ? p : Math.abs(train().lastVec().cardinality());
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
    }
  }

  @Override public void cv_computeAndSetOptimalParameters(ModelBuilder[] cvModelBuilders) {
    _parms._overwrite_with_best_model = false;

    if( _parms._stopping_rounds == 0 && _parms._max_runtime_secs == 0) return; // No exciting changes to stopping conditions
    // Extract stopping conditions from each CV model, and compute the best stopping answer
    _parms._stopping_rounds = 0;
    _parms._max_runtime_secs = 0;
    double sum = 0;
    for( ModelBuilder cvmb : cvModelBuilders )
      sum += ((DeepLearningModel)DKV.getGet(cvmb.dest())).last_scored().epoch_counter;
    _parms._epochs = sum/cvModelBuilders.length;
    if( !_parms._quiet_mode ) {
      warn("_epochs", "Setting optimal _epochs to " + _parms._epochs + " for cross-validation main model based on early stopping of cross-validation models.");
      warn("_stopping_rounds", "Disabling convergence-based early stopping for cross-validation main model.");
      warn("_max_runtime_secs", "Disabling maximum allowed runtime for cross-validation main model.");
    }
  }
  
  @Override
  protected Frame rebalance(final Frame original_fr, boolean local, final String name) {
    if (original_fr == null) return null;
    if (_parms._force_load_balance || _parms._reproducible) { //this is called before the parameters are sanitized, so force_load_balance might be user-disabled -> so must check reproducible flag as well
      int original_chunks = -1;
      try {
        original_chunks = original_fr.anyVec().nChunks();
      } catch (NullPointerException npe) {
        npe.printStackTrace();
      }
      _job.update(0,"Load balancing " + name.substring(name.length() - 5) + " data...");
      int chunks = desiredChunks(original_fr, local);
      if (!_parms._reproducible)  {
        if (original_chunks >= chunks){
          if (!_parms._quiet_mode)
            Log.info("Dataset already contains " + original_chunks + " chunks. No need to rebalance.");
          return original_fr;
        }
      } else { //reproducible, set chunks to 1
        assert chunks == 1;
        if (!_parms._quiet_mode)
          Log.warn("Reproducibility enforced - using only 1 thread - can be slow.");
        if (original_chunks == 1)
          return original_fr;
      }
      if (!_parms._quiet_mode)
        Log.info("Rebalancing " + name.substring(name.length()-5) + " dataset into " + chunks + " chunks.");
      Key newKey = Key.make(name + ".chks" + chunks);
      RebalanceDataSet rb = new RebalanceDataSet(original_fr, newKey, chunks);
      H2O.submitTask(rb).join();
      Frame rebalanced_fr = DKV.get(newKey).get();
      Scope.track(rebalanced_fr);
      return rebalanced_fr;
    }
    return original_fr;
  }
  
  @Override
  protected int desiredChunks(final Frame original_fr, boolean local) {
    return _parms._reproducible ? 1 : (int) Math.min(4 * H2O.NUMCPUS * (local ? 1 : H2O.CLOUD.size()), original_fr.numRows());
  }

  /**
   * Compute the actual train_samples_per_iteration size from the user-given parameter
   * @param mp Model parameter (DeepLearning object)
   * @param numRows number of training rows
   * @param model DL model
   * @return The total number of training rows to be processed per iteration (summed over on all nodes)
   */
  public long computeTrainSamplesPerIteration(final DeepLearningParameters mp, final long numRows, final DeepLearningModel model) {
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
        total_gflops += hb._gflops; //can be NaN if not yet run
      }
      if (mp._single_node_mode) total_gflops /= H2O.CLOUD.size();
      if (Double.isNaN(total_gflops)) {
        total_gflops = Linpack.run(H2O.SELF._heartbeat._cpus_allowed) * (mp._single_node_mode ? 1 : H2O.CLOUD.size());
      }
      assert(!Double.isNaN(total_gflops));

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
      assert(!Double.isNaN(time_per_row_us));

      // compute the optimal number of training rows per iteration
      // fraction := time_comm_us / (time_comm_us + tspi * time_per_row_us)  ==>  tspi = (time_comm_us/fraction - time_comm_us)/time_per_row_us
      tspi = (long)((model.time_for_communication_us / fraction - model.time_for_communication_us)/ time_per_row_us);

      tspi = Math.min(tspi, (mp._single_node_mode ? 1 : H2O.CLOUD.size()) * numRows * 10); //not more than 10x of what train_samples_per_iteration=-1 would do

      // If the number is close to a multiple of epochs, use that -> prettier scoring
      if (tspi > numRows && Math.abs(tspi % numRows)/(double)numRows < 0.2)  tspi -= tspi % numRows;
      tspi = Math.min(tspi, (long)(mp._epochs * numRows / 10)); //limit to number of epochs desired, but at least 10 iterations total
      if (H2O.CLOUD.size() == 1 || mp._single_node_mode) {
        tspi = Math.min(tspi, 10*(int)(1e6/time_per_row_us)); //in single-node mode, only run for at most 10 seconds
      }
      tspi = Math.max(1, tspi); //at least 1 row
      tspi = Math.min(100000*H2O.CLOUD.size(), tspi); //at most 100k rows per node for initial guess - can always relax later on

      if (!mp._quiet_mode) {
        Log.info("Auto-tuning parameter 'train_samples_per_iteration':");
        Log.info("Estimated compute power : " + Math.round(total_gflops*100)/100 + " GFlops");
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
    return tspi;
  }
}
