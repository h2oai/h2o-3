package hex.schemas;

import hex.word2vec.Word2Vec;
import hex.word2vec.Word2VecModel;
import water.Job;
import water.api.Handler;
import water.api.JobV2;
import water.api.Schema;

@Deprecated
public class Word2VecHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public Word2VecHandler() {}
  public Word2VecV2 train(int version, Word2VecV2 s) {
    Word2Vec builder = s.createAndFillImpl();
    Word2VecModel.Word2VecParameters parms = builder._parms;
    assert parms != null; /* impl._job = */
    builder.trainModel();
    s.job = (JobV2)Schema.schema(version, Job.class).fillFromImpl(builder);
    return s;
  }
}