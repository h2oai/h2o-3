package hex.word2vec;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.word2vec.Word2VecModel.*;
import water.util.Log;

public class Word2Vec extends ModelBuilder<Word2VecModel,Word2VecModel.Word2VecParameters,Word2VecModel.Word2VecOutput> {
  public enum WordModel { SkipGram }
  public enum NormModel { HSM }

  @Override public ModelCategory[] can_build() { return new ModelCategory[]{ ModelCategory.Unknown, }; }
  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; }

  public Word2Vec(boolean startup_once) {
    super(new Word2VecParameters(), startup_once);
  }

  public Word2Vec(Word2VecModel.Word2VecParameters parms) {
    super(parms);
    init(false);
  }

  @Override protected Word2VecDriver trainModelImpl() { return new Word2VecDriver(); }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".
   *
   *  Verify that at the first column contains strings. Validate _vecSize, windowSize,
   *  sentSampleRate, initLearningRate, and epochs for values within range.
   */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (_parms._train != null) { // Can be called without an existing frame, but when present check for a string col
      if (_parms.train().vecs().length == 0 || ! _parms.trainVec().isString())
        error("_train", "The first column of the training input frame has to be column of Strings.");
    }
    if (_parms._vecSize > Word2VecParameters.MAX_VEC_SIZE) error("_vecSize", "Requested vector size of "+_parms._vecSize+" in Word2Vec, exceeds limit of "+Word2VecParameters.MAX_VEC_SIZE+".");
    if (_parms._vecSize < 1) error("_vecSize", "Requested vector size of " + _parms._vecSize + " in Word2Vec, is not allowed.");
    if (_parms._windowSize < 1) error("_windowSize", "Negative window size not allowed for Word2Vec.  Expected value > 0, received " + _parms._windowSize);
    if (_parms._sentSampleRate < 0.0) error("_sentSampleRate", "Negative sentence sample rate not allowed for Word2Vec.  Expected a value > 0.0, received " + _parms._sentSampleRate);
    if (_parms._initLearningRate < 0.0) error("_initLearningRate", "Negative learning rate not allowed for Word2Vec.  Expected a value > 0.0, received " + _parms._initLearningRate);
    if (_parms._epochs < 1) error("_epochs", "Negative epoch count not allowed for Word2Vec.  Expected value > 0, received " + _parms._epochs);
  }

  @Override
  protected void ignoreBadColumns(int npredictors, boolean expensive) {
    // Do not remove String columns - these are the ones we need!
  }

  private class Word2VecDriver extends Driver {
    @Override public void computeImpl() {
      Word2VecModel model = null;
      try {
        init(true);

        // The model to be built
        model = new Word2VecModel(_job._result, _parms, new Word2VecOutput(Word2Vec.this));
        model.delete_and_lock(_job);

        Log.info("Word2Vec: Initializing model training.");
        Word2VecModelInfo modelInfo = Word2VecModelInfo.createInitialModelInfo(_parms);

        // main loop
        Log.info("Word2Vec: Starting to train model, " + _parms._epochs + " epochs.");
        long tstart = System.currentTimeMillis();
        for (int i = 0; i < _parms._epochs; i++) {
          long start = System.currentTimeMillis();
          WordVectorTrainer trainer = new WordVectorTrainer(modelInfo).doAll(_parms.trainVec());
          long stop = System.currentTimeMillis();
          long actProcessedWords = trainer._processedWords;
          long estProcessedWords = trainer._nodeProcessedWords._val;
          if (estProcessedWords < 0.95 * actProcessedWords)
            Log.warn("Estimated number processed words " + estProcessedWords +
                    " is significantly lower than actual number processed words " + actProcessedWords);
          trainer.updateModelInfo(modelInfo);
          model.update(_job); // Early version of model is visible
          _job.update(1);
          double duration = (stop - start) / 1000.0;
          Log.info("Epoch " + i + " took "  + duration + "s; Words trained/s: " + actProcessedWords / duration);
        }
        long tstop  = System.currentTimeMillis();
        Log.info("Total time: " + (tstop - tstart) / 1000.0);
        Log.info("Finished training the Word2Vec model.");
        model.buildModelOutput(modelInfo);
      } finally {
        if (model != null) model.unlock(_job);
      }
    }
  }
}
