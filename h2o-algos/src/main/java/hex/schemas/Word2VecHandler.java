package hex.schemas;

import hex.word2vec.Word2Vec;
import hex.word2vec.Word2VecModel;
import water.H2O;
import water.api.Handler;

public class Word2VecHandler extends Handler<Word2Vec,Word2VecV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public Word2VecHandler() {}
  public Word2VecV2 train(int version, Word2Vec builder) {
    Word2VecModel.Word2VecParameters parms = builder._parms;
    assert parms != null;
    builder.train();
    Word2VecV2 schema = schema(version);
    schema.parameters = new Word2VecV2.Word2VecParametersV2();
    schema.job = builder._key;
    return schema;
  }
  @Override protected Word2VecV2 schema(int version) { return new Word2VecV2(); }
  @Override public void compute2() { throw H2O.fail(); }
}