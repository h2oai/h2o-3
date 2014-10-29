package water.string;

import org.junit.*;
import water.*;
import water.util.Log;
import water.fvec.Frame;
import water.string.Word2VecParameters.*;

public class Word2VecTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(3); }

  //These tests only test that the code runs.
  // Given the statistical nature of word2vec,
  // resulting vectors differ significantly from
  // each run.  This makes it difficult to get
  // a repeatable result out of short tests.
  // This needs to be revisited.
  @Test
  public void testSkipGram_HSM() {
    Frame fr = null, w2vFr = null;
    Key wca = null;
    try {
      long timeA = System.currentTimeMillis();
    fr = parse_test_file("smalldata/text/text8");
      long timeB = System.currentTimeMillis();
      Log.info("Done parsing: "+(float)(timeB-timeA)/1000+"s");
    wca = (new WordCountTask(5)).doAll(fr)._wordCountKey;
      timeA = System.currentTimeMillis();
      Log.info("Done counting words: "+(float)(timeA-timeB)/1000+"s");
    Word2VecParameters p = new Word2VecParameters(WordModel.SkipGram, NormModel.HSM, wca, 25, 4, 0.001f, 0.025f, 2);
      timeB = System.currentTimeMillis();
      Log.info("Done creating params: "+(float)(timeB-timeA)/1000+"s");
    Word2Vec w2v = new Word2Vec(p, fr);
      timeA = System.currentTimeMillis();
      Log.info("Done training: "+(float)(timeA-timeB)/1000+"s");
    w2v.findSynonyms("cat", 5);
      w2vFr = w2v._w2vFrame;
    } finally {
      if( fr  != null ) fr.remove();
      if( wca != null ) wca.remove();
      if( w2vFr != null ) w2vFr.remove();
    }
  }

  @Test
  public void testSkipGram_NS() {
    Frame fr = null, w2vFr = null;
    Key wca = null;
    try {
      long timeA = System.currentTimeMillis();
      fr = parse_test_file("smalldata/text/text8");
      long timeB = System.currentTimeMillis();
      Log.info("Done parsing: "+(float)(timeB-timeA)/1000+"s");
      wca = (new WordCountTask(5)).doAll(fr)._wordCountKey;
      timeA = System.currentTimeMillis();
      Log.info("Done counting words: "+(float)(timeA-timeB)/1000+"s");
      Word2VecParameters p = new Word2VecParameters(WordModel.SkipGram, NormModel.NegSampling, 5, wca, 25, 4, 0.001f, 0.025f, 2);
      timeB = System.currentTimeMillis();
      Log.info("Done creating params: "+(float)(timeB-timeA)/1000+"s");
      Word2Vec w2v = new Word2Vec(p, fr);
      timeA = System.currentTimeMillis();
      Log.info("Done training: "+(float)(timeA-timeB)/1000+"s");
      w2v.findSynonyms("cat", 5);
      w2vFr = w2v._w2vFrame;
    } finally {
      if( fr  != null ) fr.remove();
      if( wca != null ) wca.remove();
      if( w2vFr != null ) w2vFr.remove();
    }
  }

  @Test
  public void testCBOW_HSM() {
    Frame fr = null, w2vFr = null;
    Key wca = null;
    try {
      long timeA = System.currentTimeMillis();
      fr = parse_test_file("smalldata/text/text8");
      long timeB = System.currentTimeMillis();
      Log.info("Done parsing: "+(float)(timeB-timeA)/1000+"s");
      wca = (new WordCountTask(5)).doAll(fr)._wordCountKey;
      timeA = System.currentTimeMillis();
      Log.info("Done counting words: "+(float)(timeA-timeB)/1000+"s");
      Word2VecParameters p = new Word2VecParameters(WordModel.CBOW, NormModel.HSM, wca, 25, 4, 0.001f, 0.025f, 2);
      timeB = System.currentTimeMillis();
      Log.info("Done creating params: "+(float)(timeB-timeA)/1000+"s");
      Word2Vec w2v = new Word2Vec(p, fr);
      timeA = System.currentTimeMillis();
      Log.info("Done training: "+(float)(timeA-timeB)/1000+"s");
      w2v.findSynonyms("cat", 5);
      w2vFr = w2v._w2vFrame;
    } finally {
      if( fr  != null ) fr.remove();
      if( wca != null ) wca.remove();
      if( w2vFr != null ) w2vFr.remove();
    }
  }

  @Test
  public void testCBOW_NS() {
    Frame fr = null, w2vFr = null;
    Key wca = null;
    try {
      long timeA = System.currentTimeMillis();
      fr = parse_test_file("smalldata/text/text8");
      long timeB = System.currentTimeMillis();
      Log.info("Done parsing: "+(float)(timeB-timeA)/1000+"s");
      wca = (new WordCountTask(5)).doAll(fr)._wordCountKey;
      timeA = System.currentTimeMillis();
      Log.info("Done counting words: "+(float)(timeA-timeB)/1000+"s");
      Word2VecParameters p = new Word2VecParameters(WordModel.CBOW, NormModel.NegSampling, 5, wca, 25, 4, 0.001f, 0.025f, 2);
      timeB = System.currentTimeMillis();
      Log.info("Done creating params: "+(float)(timeB-timeA)/1000+"s");
      Word2Vec w2v = new Word2Vec(p, fr);
      timeA = System.currentTimeMillis();
      Log.info("Done training: "+(float)(timeA-timeB)/1000+"s");
      w2v.findSynonyms("cat", 5);
      w2vFr = w2v._w2vFrame;
    } finally {
      if( fr  != null ) fr.remove();
      if( wca != null ) wca.remove();
      if( w2vFr != null ) w2vFr.remove();
    }
  }
}
