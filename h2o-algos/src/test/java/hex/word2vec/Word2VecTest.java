package hex.word2vec;

import org.junit.*;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;

/**
 * Each test here runs a given algo for a couple of
 * minutes on a 100M dataset and then finds the top
 * synonym for dog.  The result should include the
 * word dogs.
 *
 * Data referenced here can be retrieved with
 * ./gradlew syncBigdataLaptop
 */
public class Word2VecTest extends TestUtil {
  static final String testFName = "bigdata/laptop/text8.gz";
  @BeforeClass() public static void setup() {
    File f = new File(testFName);
    if(!f.exists())
      throw new RuntimeException("File "+testFName+" not found.  Please run ./gradlew syncBigdataLaptop (or gradlew.bat syncBigdataLaptop for Windows) to retrieve the file.\"");
    stall_till_cloudsize(1);
  }

  private void printResults(HashMap<String, Float> hm) {
    TreeMap<Float, String> reversedMap = new TreeMap<>();
    for (Map.Entry entry : hm.entrySet())
      reversedMap.put((Float) entry.getValue(), (String) entry.getKey());

    for (Map.Entry entry : reversedMap.descendingMap().entrySet())
      Log.info(entry.getKey() + ", " + entry.getValue());
  }

  @Ignore
  @Test public void testW2V_CBOW_HSM() {
    Word2VecModel w2vm = null;
    Frame fr = null;
    try {
      fr = parse_test_file(testFName);

      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._minWordFreq = 20;
      parms._wordModel = Word2Vec.WordModel.CBOW;
      parms._normModel = Word2Vec.NormModel.HSM;
      parms._vecSize = 100;
      parms._windowSize = 4;
      parms._sentSampleRate = 0.01f;
      parms._initLearningRate = 0.05f;
      parms._epochs = 25;
      w2vm = new Word2Vec(parms).trainModel().get();
      HashMap hm = w2vm.findSynonyms("dog",10);
      printResults(hm);
      Assert.assertTrue(hm.containsKey("dogs"));
    } finally {
      if( fr  != null ) fr .remove();
      if( w2vm != null) w2vm.delete();
    }
  }

  @Ignore
  @Test public void testW2V_CBOW_NS() {
    Word2VecModel w2vm = null;
    Frame fr = null;
    try {
      fr = parse_test_file(testFName);

      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._minWordFreq = 20;
      parms._wordModel = Word2Vec.WordModel.CBOW;
      parms._normModel = Word2Vec.NormModel.NegSampling;
      parms._negSampleCnt = 15;
      parms._vecSize = 100;
      parms._windowSize = 4;
      parms._sentSampleRate = 0.01f;
      parms._initLearningRate = 0.05f;
      parms._epochs = 15;
      w2vm = new Word2Vec(parms).trainModel().get();
      HashMap hm = w2vm.findSynonyms("dog",10);
      printResults(hm);
      Assert.assertTrue(hm.containsKey("dogs"));
    } finally {
      if( fr  != null ) fr .remove();
      if( w2vm != null) w2vm.delete();
    }
  }

  @Ignore
  @Test public void testW2V_SG_HSM() {
    Word2VecModel w2vm = null;
    Frame fr = null;
    try {
      fr = parse_test_file(testFName);

      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._minWordFreq = 20;
      parms._wordModel = Word2Vec.WordModel.SkipGram;
      parms._normModel = Word2Vec.NormModel.HSM;
      parms._vecSize = 100;
      parms._windowSize = 4;
      parms._sentSampleRate = 0.001f;
      parms._initLearningRate = 0.05f;
      parms._epochs = 10;
      w2vm = new Word2Vec(parms).trainModel().get();
      HashMap hm = w2vm.findSynonyms("dog",10);
      printResults(hm);
      Assert.assertTrue(hm.containsKey("dogs"));
    } finally {
      if( fr  != null ) fr .remove();
      if( w2vm != null) w2vm.delete();
    }
  }

  @Ignore
  @Test public void testW2V_SG_NS() {
    Word2VecModel w2vm = null;
    Frame fr = null;
    try {
      fr = parse_test_file(testFName);

      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._minWordFreq = 20;
      parms._wordModel = Word2Vec.WordModel.SkipGram;
      parms._normModel = Word2Vec.NormModel.NegSampling;
      parms._negSampleCnt = 5;
      parms._vecSize = 100;
      parms._windowSize = 4;
      parms._sentSampleRate = 0.001f;
      parms._initLearningRate = 0.025f;
      parms._epochs = 15;
      w2vm = new Word2Vec(parms).trainModel().get();
      HashMap hm = w2vm.findSynonyms("dog",10);
      printResults(hm);
      Assert.assertTrue(hm.containsKey("dogs"));
    } finally {
      if( fr  != null ) fr .remove();
      if( w2vm != null) w2vm.delete();
    }
  }

  @Test public void testWordCount() {
    Key wca = null;
    Frame fr = null;
    try {
      long start = System.currentTimeMillis();
      fr = parse_test_file(testFName);
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
