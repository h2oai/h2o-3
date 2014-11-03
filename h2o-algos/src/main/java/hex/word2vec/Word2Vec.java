package hex.word2vec;


import hex.ModelBuilder;
import hex.schemas.Word2VecV2;
import hex.schemas.ModelBuilderSchema;
import water.*;
import water.fvec.*;
import water.util.Log;


public class Word2Vec extends ModelBuilder<Word2VecModel,Word2VecModel.Word2VecParameters,Word2VecModel.Word2VecOutput> {
  public enum WordModel { SkipGram, CBOW }
  public enum NormModel { HSM, NegSampling }

  public Word2Vec(Word2VecModel.Word2VecParameters parms) { super("Word2Vec", parms); }

  public ModelBuilderSchema schema() { return new Word2VecV2(); }

  /** Start the KMeans training Job on an F/J thread. */
  @Override public Job<Word2VecModel> train() {
    if (_parms.sanityCheckParameters() > 0)
      throw new IllegalArgumentException("Invalid parameters for Word2Vec: " + _parms.validationErrors());

    return start(new Word2VecDriver(), _parms._epochs);
  }

  // ----------------------
  private class Word2VecDriver extends H2O.H2OCountedCompleter<Word2VecDriver> {
    @Override
    protected void compute2() {
      try {
        buildModel();
      } catch (Throwable t) {
        t.printStackTrace();
        cancel2(t);
        throw t;
      } finally {
        done();                 // Job done!
      }
      tryComplete();
    }
  }

  Key self() { return _key; }

  /**
   * Train a word2vec model, assumes that all members are populated
   *
   */
  public final void buildModel() {
    Word2VecModel m = null;
    Frame tra_fr = _parms.train();

    m = initModel();
    trainModel(m);
  }

  /**
   * Create an initial Word2Vec model, typically to be trained by trainModel(model)
   * @return Randomly initialized model
   */
  public final Word2VecModel initModel() {
    try {
      _parms.lock_frames(Word2Vec.this);
      if (_parms.sanityCheckParameters() > 0)
        throw new IllegalArgumentException("Error(s) in model parameters: " + _parms.validationErrors());
      final Word2VecModel model = new Word2VecModel(dest(), _parms.train(), (Word2VecModel.Word2VecParameters)_parms.clone());
      model.delete_and_lock(self());
      return model;
    }
    finally {
      _parms.unlock_frames(Word2Vec.this);
    }
  }

  /**
   * Train a Word2Vec neural net model
   * @param model Input model (e.g., from initModel(), or from a previous training run)
   * @return Trained model
   */
  public final Word2VecModel trainModel(Word2VecModel model) {
    long start, stop, lastCnt=0;
    float tDiff;
    try {
      _parms.lock_frames(Word2Vec.this);
      if (model == null) {
        model = DKV.get(dest()).get();
      }
      Log.info("Starting to train the Word2Vec model.");

      // main loop
      for (int i = 0; i < _parms._epochs; i++) {
        start = System.currentTimeMillis();
        model.setModelInfo(new WordVectorTrainer(model.getModelInfo()).doAll(_parms.train()).getModelInfo());
        stop = System.currentTimeMillis();
        model.getModelInfo().updateLearningRate();
        model.update(_key); // Early version of model is visible
        tDiff = (float)(stop-start)/1000;
        Log.info("Epoch "+i+" "+tDiff+"s  Words trained/s: "+ (model.getModelInfo().getTotalProcessed()-lastCnt)/tDiff);
        lastCnt = model.getModelInfo().getTotalProcessed();
      }
      Log.info("Finished training the Word2Vec model.");
      model.buildModelOutput();
    }
    catch(RuntimeException ex) {
      model = DKV.get(dest()).get();
      _state = JobState.CANCELLED; //for JSON REST response
      Log.info("Word2Vec model building was cancelled.");
      throw ex;
    }
    finally {
      _parms.unlock_frames(Word2Vec.this);
      if (model != null) model.unlock(self());
    }
    return model;
  }
}