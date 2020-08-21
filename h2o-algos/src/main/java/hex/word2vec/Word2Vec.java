package hex.word2vec;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.word2vec.Word2VecModel.*;
import water.Job;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.StringUtils;

import java.util.LinkedList;
import java.util.List;

public class Word2Vec extends ModelBuilder<Word2VecModel,Word2VecModel.Word2VecParameters,Word2VecModel.Word2VecOutput> {
  public enum WordModel { SkipGram, CBOW }
  public enum NormModel { HSM }

  @Override public ModelCategory[] can_build() { return new ModelCategory[]{ ModelCategory.WordEmbedding, }; }
  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Stable; }
  @Override public boolean isSupervised() { return false; }

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
   *  Verify that at the first column contains strings. Validate _vec_size, _window_size,
   *  _sent_sample_rate, _init_learning_rate, and epochs for values within range.
   */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (_parms._train != null) { // Can be called without an existing frame, but when present check for a string col
      if (_parms.train().vecs().length == 0 || ! _parms.trainVec().isString())
        error("_train", "The first column of the training input frame has to be column of Strings.");
    }
    if (_parms._vec_size > Word2VecParameters.MAX_VEC_SIZE) error("_vec_size", "Requested vector size of "+_parms._vec_size +" in Word2Vec, exceeds limit of "+Word2VecParameters.MAX_VEC_SIZE+".");
    if (_parms._vec_size < 1) error("_vec_size", "Requested vector size of " + _parms._vec_size + " in Word2Vec, is not allowed.");
    if (_parms._window_size < 1) error("_window_size", "Negative window size not allowed for Word2Vec.  Expected value > 0, received " + _parms._window_size);
    if (_parms._sent_sample_rate < 0.0) error("_sent_sample_rate", "Negative sentence sample rate not allowed for Word2Vec.  Expected a value > 0.0, received " + _parms._sent_sample_rate);
    if (_parms._init_learning_rate < 0.0) error("_init_learning_rate", "Negative learning rate not allowed for Word2Vec.  Expected a value > 0.0, received " + _parms._init_learning_rate);
    if (_parms._epochs < 1) error("_epochs", "Negative epoch count not allowed for Word2Vec.  Expected value > 0, received " + _parms._epochs);
  }

  @Override
  protected void ignoreBadColumns(int npredictors, boolean expensive) {
    // Do not remove String columns - these are the ones we need!
  }

  @Override
  public boolean haveMojo() { return true; }

  private class Word2VecDriver extends Driver {
    @Override public void computeImpl() {
      Word2VecModel model = null;
      try {
        init(! _parms.isPreTrained()); // expensive == true IFF the model is not pre-trained

        // The model to be built
        model = new Word2VecModel(_job._result, _parms, new Word2VecOutput(Word2Vec.this));
        model.delete_and_lock(_job);

        if (_parms.isPreTrained())
          convertToModel(_parms._pre_trained.get(), model);
        else
          trainModel(model);
      } finally {
        if (model != null) model.unlock(_job);
      }
    }
    private void trainModel(Word2VecModel model) {
      Log.info("Word2Vec: Initializing model training.");
      Word2VecModelInfo modelInfo = Word2VecModelInfo.createInitialModelInfo(_parms);

      // main loop
      Log.info("Word2Vec: Starting to train model, " + _parms._epochs + " epochs.");
      long tstart = System.currentTimeMillis();
      for (int i = 0; i < _parms._epochs; i++) {
        long start = System.currentTimeMillis();
        WordVectorTrainer trainer = new WordVectorTrainer(_job, modelInfo).doAll(_parms.trainVec());
        long stop = System.currentTimeMillis();
        long actProcessedWords = trainer._processedWords;
        long estProcessedWords = trainer._nodeProcessedWords._val;
        if (estProcessedWords < 0.95 * actProcessedWords)
          Log.warn("Estimated number processed words " + estProcessedWords +
                  " is significantly lower than actual number processed words " + actProcessedWords);
        trainer.updateModelInfo(modelInfo);
        model.update(_job); // Early version of model is visible
        double duration = (stop - start) / 1000.0;
        Log.info("Epoch " + i + " took "  + duration + "s; Words trained/s: " + actProcessedWords / duration);
        model._output._epochs=i;

        if (stop_requested()) { // do at least one iteration to avoid null model being returned and all hell will break loose
          break;
        }
      }
      long tstop  = System.currentTimeMillis();
      Log.info("Total time: " + (tstop - tstart) / 1000.0);
      Log.info("Finished training the Word2Vec model.");
      model.buildModelOutput(modelInfo);
    }
    private void convertToModel(Frame preTrained, Word2VecModel model) {
      if (_parms._vec_size != preTrained.numCols() - 1) {
        throw new IllegalStateException("Frame with pre-trained model doesn't conform to the specified vector length.");
      }
      WordVectorConverter result = new WordVectorConverter(_job, _parms._vec_size, (int) preTrained.numRows()).doAll(preTrained);
      model.buildModelOutput(result._words, result._syn0);
    }
  }

  public static Job<Word2VecModel> fromPretrainedModel(Frame model) {
    if (model == null || model.numCols() < 2) {
      throw new IllegalArgumentException("Frame representing an external word2vec needs to have at least 2 columns.");
    }
    if (model.vec(0).get_type() != Vec.T_STR) {
      throw new IllegalArgumentException("First column is expected to contain the dictionary words and be represented as String, " +
              "instead got " + model.vec(0).get_type_str());
    }
    List<String> colErrors = new LinkedList<>();
    for (int i = 1; i < model.numCols(); i++) {
      if (model.vec(i).get_type() != Vec.T_NUM) {
        colErrors.add(model.name(i) + " (type " + model.vec(i).get_type_str() + ")");
      }
    }
    if (! colErrors.isEmpty()) {
      throw new IllegalArgumentException("All components of word2vec mapping are expected to be numeric. Invalid columns: " +
              StringUtils.join(", ", colErrors));
    }

    Word2VecModel.Word2VecParameters p = new Word2VecModel.Word2VecParameters();
    p._vec_size = model.numCols() - 1;
    p._pre_trained = model._key;
    return new Word2Vec(p).trainModel();
  }

}
