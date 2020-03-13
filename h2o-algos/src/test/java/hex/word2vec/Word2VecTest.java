package hex.word2vec;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class Word2VecTest extends TestUtil {

  @Rule
  public ExpectedException ee = ExpectedException.none();
  
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
      p._min_word_freq = 5;
      p._word_model = Word2Vec.WordModel.SkipGram;
      p._norm_model = Word2Vec.NormModel.HSM;
      p._vec_size = 10;
      p._window_size = 5;
      p._sent_sample_rate = 0.001f;
      p._init_learning_rate = 0.025f;
      p._epochs = 1;

      Word2VecModel w2vm = (Word2VecModel) Scope.track_generic(new Word2Vec(p).trainModel().get());

      Map<String, Float> hm = w2vm.findSynonyms("a", 2);
      logResults(hm);
      assertEquals(new HashSet<>(Arrays.asList("b", "c")), hm.keySet());

      Vec testWordVec = Scope.track(svec("a", "b", "c", "Unseen", null));
      Frame wv = Scope.track(w2vm.transform(testWordVec, Word2VecModel.AggregateMethod.NONE));
      assertEquals(10, wv.numCols());
      for (int i = 0; i < 10; i++) {
        for (int j = 0; j < 3; j++)
          assertFalse(wv.vec(i).isNA(j)); // known words
        for (int j = 3; j < 5; j++)
          assertTrue(wv.vec(i).isNA(j)); // unseen & missing words
      }

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testW2V_pretrained() {
    String[] words = new String[1000];
    double[] v1 = new double[words.length];
    double[] v2 = new double[words.length];
    for (int i = 0; i < words.length; i++) {
      words[i] = "word" + i;
      v1[i] = i / (float) words.length;
      v2[i] = 1 - v1[i];
    }
    Scope.enter();
    Frame pretrained = new TestFrameBuilder()
            .withName("w2v-pretrained")
            .withColNames("Word", "V1", "V2")
            .withVecTypes(Vec.T_STR, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, words)
            .withDataForCol(1, v1)
            .withDataForCol(2, v2)
            .withChunkLayout(100, 100, 20, 80, 100, 100, 100, 100, 100, 100, 100)
            .build();
    Scope.track(pretrained);
    try {
      Word2VecModel w2vm = Word2Vec.fromPretrainedModel(pretrained).get();
      Scope.track_generic(w2vm);

      for (int i = 0; i < words.length; i++) {
        float[] wordVector = w2vm.transform(words[i]);
        assertArrayEquals("wordvec " + i, new float[]{(float) v1[i], (float) v2[i]}, wordVector, 0.0001f);
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testImportPretrained_invalid() {
    try {
      Scope.enter();
      Frame pretrained = new TestFrameBuilder()
              .withName("w2v-pretrained")
              .withColNames("Word", "V1", "V2", "V3")
              .withVecTypes(Vec.T_STR, Vec.T_TIME, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar("a"))
              .withDataForCol(1, ar(System.currentTimeMillis()))
              .withDataForCol(2, ard(Math.PI))
              .withDataForCol(3, ar("C1"))
              .build();
      ee.expectMessage("All components of word2vec mapping are expected to be numeric. Invalid columns: V1 (type Time), V3 (type Enum)");
      Word2Vec.fromPretrainedModel(pretrained);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testW2V_toFrame() {
    Random r = new Random();
    String[] words = new String[1000];
    double[] v1 = new double[words.length];
    double[] v2 = new double[words.length];
    for (int i = 0; i < words.length; i++) {
      words[i] = "word" + i;
      v1[i] = r.nextDouble();
      v2[i] = r.nextDouble();
    }
    try {
      Scope.enter();
      Frame expected = new TestFrameBuilder()
              .withName("w2v")
              .withColNames("Word", "V1", "V2")
              .withVecTypes(Vec.T_STR, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, words)
              .withDataForCol(1, v1)
              .withDataForCol(2, v2)
              .withChunkLayout(100, 900)
              .build();
      Scope.track(expected);
      Word2VecModel.Word2VecParameters p = new Word2VecModel.Word2VecParameters();
      p._vec_size = 2;
      p._pre_trained = expected._key;
      Word2VecModel w2vm = (Word2VecModel) Scope.track_generic(new Word2Vec(p).trainModel().get());

      // convert to a Frame
      Frame result = Scope.track(w2vm.toFrame());

      assertArrayEquals(expected._names, result._names);
      assertStringVecEquals(expected.vec(0), result.vec(0));
      assertVecEquals(expected.vec(1), result.vec(1), 0.0001);
      assertVecEquals(expected.vec(2), result.vec(2), 0.0001);
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
      p._min_word_freq = 5;
      p._word_model = Word2Vec.WordModel.SkipGram;
      p._norm_model = Word2Vec.NormModel.HSM;
      p._vec_size = 100;
      p._window_size = 4;
      p._sent_sample_rate = 0.001f;
      p._init_learning_rate = 0.025f;
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

  @Test public void testW2V_CBOW_HSM() {
    assumeThat("word2vec test enabled", System.getProperty("testW2V"), is(notNullValue())); // ignored by default

    Frame fr = parse_test_file("bigdata/laptop/text8.gz", "NA", 0, new byte[]{Vec.T_STR});
    Word2VecModel w2vm = null;
    try {
      Word2VecModel.Word2VecParameters parms = new Word2VecModel.Word2VecParameters();
      parms._train = fr._key;
      parms._min_word_freq = 20;
      parms._word_model = Word2Vec.WordModel.CBOW;
      parms._norm_model = Word2Vec.NormModel.HSM;
      parms._vec_size = 100;
      parms._window_size = 4;
      parms._sent_sample_rate = 0.01f;
      parms._init_learning_rate = 0.05f;
      parms._epochs = 10;
      w2vm = new Word2Vec(parms).trainModel().get();
      Map<String, Float> hm = w2vm.findSynonyms("dog", 10);
      logResults(hm);
      assertTrue(hm.containsKey("dogs"));
      assertTrue(hm.containsKey("cat") || hm.containsKey("dogs") || hm.containsKey("hound"));
    } finally {
      fr.remove();
      if (w2vm != null) w2vm.delete();
    }
  }

  @Test
  public void testTransformAggregate() {
    Scope.enter();
    try {
      Vec v = Scope.track(svec("a", "b"));
      Frame fr = Scope.track(new Frame(Key.<Frame>make(), new String[]{"Words"}, new Vec[]{v}));
      DKV.put(fr);

      // build an arbitrary w2v model & overwrite the learned vector with fixed values
      Word2VecModel.Word2VecParameters p = new Word2VecModel.Word2VecParameters();
      p._train = fr._key;
      p._min_word_freq = 0;
      p._epochs = 1;
      p._vec_size = 2;
      Word2VecModel w2vm = (Word2VecModel) Scope.track_generic(new Word2Vec(p).trainModel().get());
      w2vm._output._vecs = new float[] {1.0f, 0.0f, 0.0f, 1.0f};
      DKV.put(w2vm);

      String[][] chunks = {
              new String[] {"a", "b", null, "a", "c", null, "c", null, "a", "a"},
              new String[] {"a", "b", null},
              new String[] {null, null},
              new String[] {"b", "b", "a"},
              new String[] {"b"} // no terminator at the end
      };
      long[] layout = new long[chunks.length];
      String[] sentences = new String[0];
      for (int i = 0; i < chunks.length; i++) {
        sentences = ArrayUtils.append(sentences, chunks[i]);
        layout[i] = chunks[i].length;
      }

      Frame f = new TestFrameBuilder()
              .withName("data")
              .withColNames("Sentences")
              .withVecTypes(Vec.T_STR)
              .withDataForCol(0, sentences)
              .withChunkLayout(layout)
              .build();

      Frame result = Scope.track(w2vm.transform(f.vec(0), Word2VecModel.AggregateMethod.AVERAGE));
      Vec expectedAs = Scope.track(dvec(0.5, 1.0, Double.NaN, 0.75, Double.NaN, Double.NaN, 0.25));
      Vec expectedBs = Scope.track(dvec(0.5, 0.0, Double.NaN, 0.25, Double.NaN, Double.NaN, 0.75));

      assertVecEquals(expectedAs, result.vec(w2vm._output._vocab.get(new BufferedString("a"))), 0.0001);
      assertVecEquals(expectedBs, result.vec(w2vm._output._vocab.get(new BufferedString("b"))), 0.0001);
    } finally {
      Scope.exit();
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
