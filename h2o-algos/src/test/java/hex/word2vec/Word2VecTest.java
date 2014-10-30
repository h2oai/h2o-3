package hex.word2vec;

import org.junit.*;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

public class Word2VecTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testW2V_CBOW_HSM() {
    Word2Vec job = null;
    Word2VecModel w2vm = null;
    Word2VecModel.Word2VecOutput w2vmo = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/text/text8");

      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._wordModel = Word2Vec.WordModel.CBOW;
      parms._normModel = Word2Vec.NormModel.HSM;
      parms._vecSize = 25;
      parms._windowSize = 4;
      parms._sentSampleRate = 0.001f;
      parms._initLearningRate = 0.025f;
      parms._epochs = 2;
      job = new Word2Vec(parms);
      job.train();
      w2vm = job.get();
      w2vmo = w2vm._output;
      w2vmo.findSynonyms("cat",5);

    } finally {
      if( fr  != null ) fr .remove();
      if( job != null) job.remove();
      if( w2vm != null) w2vm.delete();
    }
  }

  @Test public void testW2V_CBOW_NS() {
    Word2Vec job = null;
    Word2VecModel w2vm = null;
    Word2VecModel.Word2VecOutput w2vmo = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/text/text8");

      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._wordModel = Word2Vec.WordModel.CBOW;
      parms._normModel = Word2Vec.NormModel.NegSampling;
      parms._numNegEx = 10;
      parms._vecSize = 25;
      parms._windowSize = 4;
      parms._sentSampleRate = 0.001f;
      parms._initLearningRate = 0.025f;
      parms._epochs = 2;
      job = new Word2Vec(parms);
      job.train();
      w2vm = job.get();
      w2vmo = w2vm._output;
      w2vmo.findSynonyms("cat",5);

    } finally {
      if( fr  != null ) fr .remove();
      if( job != null) job.remove();
      if( w2vm != null) w2vm.delete();
    }
  }


  @Test public void testW2V_SG_HSM() {
    Word2Vec job = null;
    Word2VecModel w2vm = null;
    Word2VecModel.Word2VecOutput w2vmo = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/text/text8");

      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._wordModel = Word2Vec.WordModel.SkipGram;
      parms._normModel = Word2Vec.NormModel.HSM;
      parms._vecSize = 25;
      parms._windowSize = 4;
      parms._sentSampleRate = 0.001f;
      parms._initLearningRate = 0.025f;
      parms._epochs = 2;
      job = new Word2Vec(parms);
      job.train();
      w2vm = job.get();
      w2vmo = w2vm._output;
      w2vmo.findSynonyms("cat",5);

    } finally {
      if( fr  != null ) fr .remove();
      if( job != null) job.remove();
      if( w2vm != null) w2vm.delete();
    }
  }

  @Test public void testW2V_SG_NS() {
    Word2Vec job = null;
    Word2VecModel w2vm = null;
    Word2VecModel.Word2VecOutput w2vmo = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/text/text8");

      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._wordModel = Word2Vec.WordModel.SkipGram;
      parms._normModel = Word2Vec.NormModel.NegSampling;
      parms._numNegEx = 10;
      parms._vecSize = 25;
      parms._windowSize = 4;
      parms._sentSampleRate = 0.001f;
      parms._initLearningRate = 0.025f;
      parms._epochs = 2;
      job = new Word2Vec(parms);
      job.train();
      w2vm = job.get();
      w2vmo = w2vm._output;
      w2vmo.findSynonyms("cat",5);

    } finally {
      if( fr  != null ) fr .remove();
      if( job != null) job.remove();
      if( w2vm != null) w2vm.delete();
    }
  }


  @Test public void testWordCount() {
    Key wca = null;
    Frame fr = null;
    try {
      long start = System.currentTimeMillis();
      fr = parse_test_file("smalldata/text/text8");
      System.out.println("Done Parse: "+(float)(System.currentTimeMillis()-start)/1000+"s");

      start = System.currentTimeMillis();
      wca = (new WordCountTask(3)).doAll(fr)._wordCountKey;
      System.out.println("Done counting: "+(float)(System.currentTimeMillis()-start)/1000+"s");
      Assert.assertEquals(100038l, ((Frame)wca.get()).numRows());
    } finally {
      if( fr  != null ) fr.remove();
      if( wca != null ) wca.remove();
    }
  }

}
