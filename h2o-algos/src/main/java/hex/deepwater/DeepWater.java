package hex.deepwater;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ToEigenVec;
import hex.genmodel.algos.DeepWaterMojo;
import hex.util.LinearAlgebraUtils;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.util.Log;
import water.util.PrettyPrint;

import java.util.Arrays;

import static water.util.MRUtils.sampleFrame;

/**
 * Deep Learning Neural Net implementation based on MRTask
 */
public class DeepWater extends ModelBuilder<DeepWaterModel,DeepWaterParameters,DeepWaterModelOutput> {

  /** Main constructor from Deep Learning parameters */
  public DeepWater(DeepWaterParameters parms ) {
    super(parms);
    init(false);
    //createDeepWaterBackend(parms._backend.toString());
  }

  public DeepWater(DeepWaterParameters parms, Key<DeepWaterModel> key ) {
    super(parms,key);
    init(false);
    //createDeepWaterBackend(parms._backend.toString());
  }

  public DeepWater(boolean startup_once ) { super(new DeepWaterParameters(),startup_once); }

  static public boolean haveBackend() {
    boolean haveBackend = false;
    for (DeepWaterParameters.Backend b : DeepWaterParameters.Backend.values()) {
      haveBackend |= (DeepWaterMojo.createDeepWaterBackend(b.toString()) != null);
      if (haveBackend) break;
    }
    return haveBackend;
  }

  static public boolean haveBackend(DeepWaterParameters.Backend backend) {
    return (DeepWaterMojo.createDeepWaterBackend(backend.toString()) != null);
  }

  @Override public BuilderVisibility builderVisibility() {
     return haveBackend() ? BuilderVisibility.Stable : BuilderVisibility.Experimental;
  }

  /** Types of models we can build with DeepWater  */
  @Override public ModelCategory[] can_build() {
    return new ModelCategory[]{
//            ModelCategory.Regression,
            ModelCategory.Binomial,
            ModelCategory.Multinomial,
//            ModelCategory.AutoEncoder
    };
  }

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
    super.init(expensive);
    _parms.validate(this, expensive);
    if (expensive && error_count() == 0) checkMemoryFootPrint();
  }

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
    public final void buildModel() {
      trainModel(new DeepWaterModel(_result,_parms,new DeepWaterModelOutput(DeepWater.this),train(),valid(),nclasses()));
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
        if(!mp._shuffle_training_data && model.actual_train_samples_per_iteration == model.training_rows && train.anyVec().nChunks()==1) {
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
        if (_parms._problem_type== DeepWaterParameters.ProblemType.image_classification) {
          bytes = train.numRows() * model.model_info()._width * model.model_info()._height * model.model_info()._channels * 4;
        } else {
          bytes = train.byteSize();
        }
        cache = mp._cache_data;
        if (cache) {
          if (bytes < H2O.CLOUD.free_mem() / 4) {
            Log.info("Automatically enabling data caching, expecting to require " + PrettyPrint.bytes(bytes) + ".");
          } else {
            Log.info("Automatically disabling data caching, since it would require too much space: " + PrettyPrint.bytes(bytes) + ".");
            mp._cache_data = false;
            cache = false;
          }
        }

        //main loop
        for(;;) {
          model.iterations++;
          model.set_model_info(mp._epochs == 0 ? model.model_info() : H2O.CLOUD.size() > 1 && mp._replicate_training_data ? (mp._single_node_mode ?
                  new DeepWaterTask2(_job._key, train, model.model_info(), rowFraction(train, mp, model), model.iterations).doAll(Key.make(H2O.SELF)).model_info() : //replicated data + single node mode
                  new DeepWaterTask2(_job._key, train, model.model_info(), rowFraction(train, mp, model), model.iterations).doAllNodes(             ).model_info()): //replicated data + multi-node mode
                  new DeepWaterTask (model.model_info(), rowFraction(train, mp, model), _job).doAll     (    train    ).model_info()); //distributed data (always in multi-node mode)
          long before = System.currentTimeMillis();
          if (_parms._export_native_model_prefix!=null && _parms._export_native_model_prefix!="") {
            Log.info("Saving model state.");
            model.exportNativeModel(_parms._export_native_model_prefix, model.iterations);
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
            if (!_parms._quiet_mode)
              Log.info("Setting the model to be the best model so far (based on scoring history).");
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
            assert(Math.abs(best_model.loss() - model.loss())<=1e-5*Math.abs(model.loss()+best_model.loss()));
          }
        }
      }
      catch(Throwable t) {
        t.printStackTrace();
      }
      finally {
        if (model.model_info()._backend != null) model.model_info().nativeToJava();
        if (cache) model.cleanUpCache();
        model.removeNativeState();
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
