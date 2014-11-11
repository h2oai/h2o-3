package hex.schemas;

import hex.word2vec.Word2Vec;
import hex.word2vec.Word2VecModel;
import water.H2O;
import water.api.Handler;

@Deprecated
public class Word2VecHandler extends Handler<Word2Vec,Word2VecV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public Word2VecHandler() {}
  public Word2VecV2 train(int version, Word2Vec builder) {
    Word2VecModel.Word2VecParameters parms = builder._parms;
    assert parms != null; /* impl._job = */
    builder.trainModel();
    Word2VecV2 schema = schema(version).fillFromImpl(builder); // TODO: superclass!
    schema.job = builder._key;
    return schema;
  }

  @Override protected Word2VecV2 schema(int version) { Word2VecV2 schema = new Word2VecV2(); schema.parameters = schema.createParametersSchema(); return schema;  }
  @Override public void compute2() { throw H2O.fail(); }
}