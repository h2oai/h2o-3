package hex.word2vec;

import hex.ModelCategory;
import water.DKV;
import water.Job;
import water.H2O;
import water.fvec.Vec;
import water.util.Log;

import hex.ModelBuilder;
import hex.schemas.Word2VecV3;
import hex.schemas.ModelBuilderSchema;
import hex.word2vec.Word2VecModel.*;

public class Word2Vec extends ModelBuilder<Word2VecModel,Word2VecModel.Word2VecParameters,Word2VecModel.Word2VecOutput> {
  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{
            ModelCategory.Unknown,
    };
  }

  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; };

  public enum WordModel { SkipGram, CBOW }
  public enum NormModel { HSM, NegSampling }

  public Word2Vec(Word2VecModel.Word2VecParameters parms) { super("Word2Vec", parms); }

  public ModelBuilderSchema schema() { return new Word2VecV3(); }

  /** Start the KMeans training Job on an F/J thread. */
  @Override public Job<Word2VecModel> trainModelImpl() { return start(new Word2VecDriver(), _parms._epochs); }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".
   *
   *  Verify that at least one column contains strings.  Validate _vecSize, windowSize,
   *  sentSampleRate, initLearningRate, and epochs for values within range.
   */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (_parms._train != null) { //Can be called without an existing frame, but when present check for a string col
      Boolean useableCol = false;
      for (Vec v : _parms.train().vecs()) if (v.isString()) useableCol = true;
      if (!useableCol) error("_train", "Training input frame lacks any string columns for Word2Vec to analyze.");
    }
    if (_parms._vecSize > Word2VecParameters.MAX_VEC_SIZE) error("_vecSize", "Requested vector size of "+_parms._vecSize+" in Word2Vec, exceeds limit of "+Word2VecParameters.MAX_VEC_SIZE+".");
    if (_parms._vecSize < 1) error("_vecSize", "Requested vector size of " + _parms._vecSize + " in Word2Vec, is not allowed.");
    if (_parms._windowSize < 1) error("_windowSize", "Negative window size not allowed for Word2Vec.  Expected value > 0, received " + _parms._windowSize);
    if (_parms._sentSampleRate < 0.0) error("_sentSampleRate", "Negative sentence sample rate not allowed for Word2Vec.  Expected a value > 0.0, received " + _parms._sentSampleRate);
    if (_parms._initLearningRate < 0.0) error("_initLearningRate", "Negative learning rate not allowed for Word2Vec.  Expected a value > 0.0, received " + _parms._initLearningRate);
    if (_parms._epochs < 1) error("_epochs", "Negative epoch count not allowed for Word2Vec.  Expected value > 0, received " + _parms._epochs);
  }

  private class Word2VecDriver extends H2O.H2OCountedCompleter<Word2VecDriver> {
    @Override
    protected void compute2() {
      Word2VecModel model = null;
      long start, stop, lastCnt=0;
      long tstart, tstop;
      float tDiff;

      try {
        _parms.read_lock_frames(Word2Vec.this);
        init(true);

        //The model to be built
        model = new Word2VecModel(dest(), _parms, new Word2VecOutput(Word2Vec.this));
        model.delete_and_lock(_key);

        // main loop
        Log.info("Word2Vec: Starting to train model.");
        tstart = System.currentTimeMillis();
        for (int i = 0; i < _parms._epochs; i++) {
          start = System.currentTimeMillis();
          model.setModelInfo(new WordVectorTrainer(model.getModelInfo()).doAll(_parms.train()).getModelInfo());
          stop = System.currentTimeMillis();
          model.getModelInfo().updateLearningRate();
          model.update(_key); // Early version of model is visible
          Job.update(1, _key);
          tDiff = (float)(stop-start)/1000;
          Log.info("Epoch "+i+" "+tDiff+"s  Words trained/s: "+ (model.getModelInfo().getTotalProcessed()-lastCnt)/tDiff);
          lastCnt = model.getModelInfo().getTotalProcessed();
        }
        tstop  = System.currentTimeMillis();
        Log.info("Total time :" + ((float)(tstop-tstart))/1000f);
        Log.info("Finished training the Word2Vec model.");
        model.buildModelOutput();
        done();                 // Job done!
      } catch (Throwable t) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          t.printStackTrace();
          failed(t);
          throw t;
        }
      } finally {
        if( model != null ) model.unlock(_key);
        _parms.read_unlock_frames(Word2Vec.this);
      }
      tryComplete();
    }
  }
}
