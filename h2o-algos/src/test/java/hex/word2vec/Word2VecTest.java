package hex.word2vec;

import org.junit.*;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.*;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assume.assumeThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class Word2VecTest extends TestUtil {

  @BeforeClass()
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testW2V_SG_HSM_small() {
    String[] words = new String[220];
    for (int i = 0; i < 200; i += 2) { words[i] = "a"; words[i + 1] = "b"; }
    for (int i = 200; i < 220; i += 2) { words[i] = "a"; words[i + 1] = "c"; }
    Scope.enter();
    try {
      Vec v = Scope.track(svec(words));
      Frame fr = Scope.track(new Frame(Key.<Frame>make(), new String[]{"Words"}, new Vec[]{v}));
      DKV.put(fr);

      Word2VecModel.Word2VecParameters p = new Word2VecModel.Word2VecParameters();
      p._train = fr._key;
      p._minWordFreq = 5;
      p._wordModel = Word2Vec.WordModel.SkipGram;
      p._normModel = Word2Vec.NormModel.HSM;
      p._vecSize = 10;
      p._windowSize = 5;
      p._sentSampleRate = 0.001f;
      p._initLearningRate = 0.025f;
      p._epochs = 1;

      Word2VecModel w2vm = (Word2VecModel) Scope.track_generic(new Word2Vec(p).trainModel().get());

      Map<String, Float> hm = w2vm.findSynonyms("a", 2);
      logResults(hm);

      assertEquals(new HashSet<>(Arrays.asList("b", "c")), hm.keySet());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testW2V_SG_HSM() {
    assumeThat("word2vec test enabled", System.getProperty("testW2V"), is(notNullValue())); // ignored by default

    Frame fr = parse_test_file("bigdata/laptop/text8.gz", "NA", 0, new byte[]{Vec.T_STR});
    Word2VecModel w2vm = null;
    try {
      Word2VecModel.Word2VecParameters p = new Word2VecModel.Word2VecParameters();
      p._train = fr._key;
      p._minWordFreq = 5;
      p._wordModel = Word2Vec.WordModel.SkipGram;
      p._normModel = Word2Vec.NormModel.HSM;
      p._vecSize = 100;
      p._windowSize = 4;
      p._sentSampleRate = 0.001f;
      p._initLearningRate = 0.025f;
      p._epochs = 10;

      w2vm = new Word2Vec(p).trainModel().get();
      Map<String, Float> hm = w2vm.findSynonyms("dog", 20);
      logResults(hm);
      assertTrue(hm.containsKey("cat") || hm.containsKey("dogs") || hm.containsKey("hound"));
    } finally {
      fr.remove();
      if( w2vm != null) w2vm.delete();
    }
  }

  private void logResults(Map<String, Float> hm) {
    List<Map.Entry<String, Float>> result = new ArrayList<>(hm.entrySet());
    Collections.sort(result, new Comparator<Map.Entry<String, Float>>() {
      @Override
      public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
        return o2.getValue().compareTo(o1.getValue()); // reverse sort
      }
    });
    int i = 0;
    for (Map.Entry entry : result)
      Log.info((i++) + ". " + entry.getKey() + ", " + entry.getValue());
  }

}
