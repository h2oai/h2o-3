package hex.word2vec;

import org.junit.*;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;

/**
 * Data referenced here can be found at:
 * wget http://mattmahoney.net/dc/text8.zip -O text8.gz
 * Spaces are replaced with newlines via:
 * gsed -e 's/\s\+/\n/g' text8 > text9
 *
 * It isn't checked in because really data set should
 * generated on the fly for this test to keep the
 * repo history from containing large binaries.
 */

public class Word2VecTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  private void printResults(HashMap<String, Float> hm) {
    TreeMap<Float, String> reversedMap = new TreeMap<Float, String>();
    for (Map.Entry entry : hm.entrySet())
      reversedMap.put((Float) entry.getValue(), (String) entry.getKey());

    //then you just access the reversedMap however you like...
    for (Map.Entry entry : reversedMap.descendingMap().entrySet())
      System.out.println(entry.getKey() + ", " + entry.getValue());
  }

  @Test public void testW2V_CBOW_HSM() {
    Word2Vec job = null;
    Word2VecModel w2vm = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/text/text8");

      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._minWordFreq = 5;
      parms._wordModel = Word2Vec.WordModel.CBOW;
      parms._normModel = Word2Vec.NormModel.HSM;
      parms._vecSize = 100;
      parms._windowSize = 5;
      parms._sentSampleRate = 0.001f;
      parms._initLearningRate = 0.05f;
      parms._epochs = 10;
      job = new Word2Vec(parms);
      job.train();
      w2vm = job.get();
      printResults(w2vm.findSynonyms("dog",5));

    } finally {
      if( fr  != null ) fr .remove();
      if( job != null) job.remove();
      if( w2vm != null) w2vm.delete();
    }
  }

  @Test public void testW2V_CBOW_NS() {
    Word2Vec job = null;
    Word2VecModel w2vm = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/text/text8");

      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._minWordFreq = 5;
      parms._wordModel = Word2Vec.WordModel.CBOW;
      parms._normModel = Word2Vec.NormModel.NegSampling;
      parms._negSampleCnt = 5;
      parms._vecSize = 100;
      parms._windowSize = 5;
      parms._sentSampleRate = 0.001f;
      parms._initLearningRate = 0.05f;
      parms._epochs = 10;
      job = new Word2Vec(parms);
      job.train();
      w2vm = job.get();
      printResults(w2vm.findSynonyms("dog",5));
    } finally {
      if( fr  != null ) fr .remove();
      if( job != null) job.remove();
      if( w2vm != null) w2vm.delete();
    }
  }


  @Test public void testW2V_SG_HSM() {
    Word2Vec job = null;
    Word2VecModel w2vm = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/text/text8");

      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._minWordFreq = 5;
      parms._wordModel = Word2Vec.WordModel.SkipGram;
      parms._normModel = Word2Vec.NormModel.HSM;
      parms._vecSize = 100;
      parms._windowSize = 5;
      parms._sentSampleRate = 0.001f;
      parms._initLearningRate = 0.025f;
      parms._epochs = 10;
      job = new Word2Vec(parms);
      job.train();
      w2vm = job.get();
      printResults(w2vm.findSynonyms("dog",5));
    } finally {
      if( fr  != null ) fr .remove();
      if( job != null) job.remove();
      if( w2vm != null) w2vm.delete();
    }
  }

  @Test public void testW2V_SG_NS() {
    Word2Vec job = null;
    Word2VecModel w2vm = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/text/text8");

      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._minWordFreq = 5;
      parms._wordModel = Word2Vec.WordModel.SkipGram;
      parms._normModel = Word2Vec.NormModel.NegSampling;
      parms._negSampleCnt = 5;
      parms._vecSize = 100;
      parms._windowSize = 5;
      parms._sentSampleRate = 0.001f;
      parms._initLearningRate = 0.025f;
      parms._epochs = 10;
      job = new Word2Vec(parms);
      job.train();
      w2vm = job.get();
      printResults(w2vm.findSynonyms("dog",5));
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
