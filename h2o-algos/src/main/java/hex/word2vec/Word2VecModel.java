package hex.word2vec;

import hex.ModelCategory;
import hex.ModelMetrics;
import water.*;
import water.fvec.*;
import water.parser.BufferedString;
import water.util.*;

import hex.Model;
import hex.word2vec.Word2VecModel.*;

import java.util.*;

public class Word2VecModel extends Model<Word2VecModel, Word2VecParameters, Word2VecOutput> {

  public Word2VecModel(Key<Word2VecModel> selfKey, Word2VecParameters params, Word2VecOutput output) {
    super(selfKey, params, output);
    assert(Arrays.equals(_key._kb, selfKey._kb));
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No Model Metrics for Word2Vec.");
  }

  @Override
  public double[] score0(Chunk[] cs, int foo, double data[], double preds[]) {
    throw H2O.unimpl();
  }

  @Override
  protected double[] score0(double data[], double preds[]) {
    throw H2O.unimpl();
  }

  /**
   * Takes an input string can return the word vector for that word.
   *
   * @param target - String of desired word
   * @return float array containing the word vector values or null if
   *  the word isn't present in the vocabulary.
   */
  public float[] transform(String target) {
    return transform(new BufferedString(target));
  }

  private float[] transform(BufferedString word) {
    if (! _output._vocab.containsKey(word))
      return null;
    int wordIdx = _output._vocab.get(word);
    return Arrays.copyOfRange(_output._vecs, wordIdx * _output._vecSize, (wordIdx + 1) * _output._vecSize);
  }

  public Frame transform(Vec wordVec) {
    if (wordVec.get_type() != Vec.T_STR) {
      throw new IllegalArgumentException("Expected a string vector, got " + wordVec.get_type_str() + " vector.");
    }
    byte[] types = new byte[_output._vecSize];
    Arrays.fill(types, Vec.T_NUM);
    return new Word2VecTransformTask(this).doAll(types, wordVec).outputFrame(Key.<Frame>make(), null, null);
  }

  private static class Word2VecTransformTask extends MRTask<Word2VecTransformTask> {
    private Word2VecModel _model;
    public Word2VecTransformTask(Word2VecModel model) { _model = model; }
    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      assert cs.length == 1;
      Chunk chk = cs[0];
      BufferedString tmp = new BufferedString();
      for (int i = 0; i < chk._len; i++) {
        if (chk.isNA(i)) {
          for (NewChunk nc : ncs) nc.addNA();
        } else {
          BufferedString word = chk.atStr(tmp, i);
          float[] vs = _model.transform(word);
          if (vs == null)
            for (NewChunk nc : ncs) nc.addNA();
          else
            for (int j = 0; j < ncs.length; j++)
              ncs[j].addNum(vs[j]);
        }
      }
    }
  }

  /**
   * Find synonyms (i.e. word-vectors with the highest cosine similarity)
   *
   * @param target String of desired word
   * @param cnt Number of synonyms to find
   */
  public Map<String, Float> findSynonyms(String target, int cnt) {
    float[] vec = transform(target);

    if ((vec == null) || (cnt == 0))
      return Collections.emptyMap();

    int[] synonyms = new int[cnt];
    float[] scores = new float[cnt];


    int min = 0;
    for (int i = 0; i < cnt; i++) {
      synonyms[i] = i;
      scores[i] = cosineSimilarity(vec, i * vec.length, _output._vecs);
      if (scores[i] < scores[min])
        min = i;
    }

    final int vocabSize = _output._vocab.size();
    for (int i = cnt; i < vocabSize; i++) {
      float score = cosineSimilarity(vec, i * vec.length, _output._vecs);
      if ((score <= scores[min]) || (score >= 0.999999))
        continue;
      synonyms[min] = i;
      scores[min] = score;
      // find a new min
      min = 0;
      for (int j = 1; j < cnt; j++)
        if (scores[j] < scores[min])
          min = j;
    }

    Map<String, Float> result = new HashMap<>(cnt);
    for (int i = 0; i < cnt; i++)
      result.put(_output._words[synonyms[i]].toString(), scores[i]);
    return result;
  }

  /**
   * Basic calculation of cosine similarity
   * @param target - a word vector
   * @param pos - position in vecs
   * @param vecs - learned word vectors
   * @return cosine similarity between the two word vectors
   */
  private float cosineSimilarity(float[] target, int pos, float[] vecs) {
    float dotProd = 0, tsqr = 0, csqr = 0;
    for(int i = 0; i < target.length; i++) {
      dotProd += target[i] * vecs[pos + i];
      tsqr += Math.pow(target[i], 2);
      csqr += Math.pow(vecs[pos + i], 2);
    }
    return (float) (dotProd / (Math.sqrt(tsqr) * Math.sqrt(csqr)));
  }

  void buildModelOutput(Word2VecModelInfo modelInfo) {
    IcedHashMapGeneric<BufferedString, Integer> vocab = ((Vocabulary) DKV.getGet(modelInfo._vocabKey))._data;
    BufferedString[] words = new BufferedString[vocab.size()];
    for (BufferedString str : vocab.keySet())
      words[vocab.get(str)] = str;

    _output._vecSize = _parms._vec_size;
    _output._vecs = modelInfo._syn0;
    _output._words = words;
    _output._vocab = vocab;
  }

  void buildModelOutput(BufferedString[] words, float[] syn0) {
    IcedHashMapGeneric<BufferedString, Integer> vocab = new IcedHashMapGeneric<>();
    for (int i = 0; i < words.length; i++)
      vocab.put(words[i], i);

    _output._vecSize = _parms._vec_size;
    _output._vecs = syn0;
    _output._words = words;
    _output._vocab = vocab;
  }

  @Override public void delete() {
    remove();
    super.delete();
  }

  public static class Word2VecParameters extends Model.Parameters {
    public String algoName() { return "Word2Vec"; }
    public String fullName() { return "Word2Vec"; }
    public String javaName() { return Word2VecModel.class.getName(); }
    @Override public long progressUnits() {
      return isPreTrained() ? _pre_trained.get().anyVec().nChunks() : train().vec(0).nChunks() * _epochs;
    }
    static final int MAX_VEC_SIZE = 10000;

    public Word2Vec.WordModel _word_model = Word2Vec.WordModel.SkipGram;
    public Word2Vec.NormModel _norm_model = Word2Vec.NormModel.HSM;
    public int _min_word_freq = 5;
    public int _vec_size = 100;
    public int _window_size = 5;
    public int _epochs = 5;
    public float _init_learning_rate = 0.025f;
    public float _sent_sample_rate = 1e-3f;
    public Key<Frame> _pre_trained;  // key of a frame that contains a pre-trained word2vec model
    boolean isPreTrained() { return _pre_trained != null; }
    Vec trainVec() { return train().vec(0); }
  }

  public static class Word2VecOutput extends Model.Output {
    public int _vecSize;
    public int _epochs;
    public Word2VecOutput(Word2Vec b) { super(b); }

    public BufferedString[] _words;
    public float[] _vecs;
    public IcedHashMapGeneric<BufferedString, Integer> _vocab;

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.WordEmbedding;
    }
  }

  public static class Word2VecModelInfo extends Iced {

    long _vocabWordCount;
    long _totalProcessedWords = 0L;

    float[] _syn0, _syn1;
    Key<HBWTree> _treeKey;
    Key<Vocabulary> _vocabKey;
    Key<WordCounts> _wordCountsKey;

    private Word2VecParameters _parameters;
    public final Word2VecParameters getParams() { return _parameters; }

    public Word2VecModelInfo() {}

    private Word2VecModelInfo(Word2VecParameters params, WordCounts wordCounts) {
      _parameters = params;

      long vocabWordCount = 0L;
      List<Map.Entry<BufferedString, IcedLong>> wordCountList = new ArrayList<>(wordCounts._data.size());
      for (Map.Entry<BufferedString, IcedLong> wc : wordCounts._data.entrySet()) {
        if (wc.getValue()._val >= _parameters._min_word_freq) {
          wordCountList.add(wc);
          vocabWordCount += wc.getValue()._val;
        }
      }
      Collections.sort(wordCountList, new Comparator<Map.Entry<BufferedString, IcedLong>>() {
        @Override
        public int compare(Map.Entry<BufferedString, IcedLong> o1, Map.Entry<BufferedString, IcedLong> o2) {
          long x = o1.getValue()._val; long y = o2.getValue()._val;
          return (x < y) ? -1 : ((x == y) ? 0 : 1);
        }
      });

      int vocabSize = wordCountList.size();
      long[] countAry = new long[vocabSize];
      Vocabulary vocab = new Vocabulary(new IcedHashMapGeneric<BufferedString, Integer>());
      int idx = 0;
      for (Map.Entry<BufferedString, IcedLong> wc : wordCountList) {
        countAry[idx] = wc.getValue()._val;
        vocab._data.put(wc.getKey(), idx++);
      }
      HBWTree t = HBWTree.buildHuffmanBinaryWordTree(countAry);

      _vocabWordCount = vocabWordCount;
      _treeKey = publish(t);
      _vocabKey = publish(vocab);
      _wordCountsKey = publish(wordCounts);

      //initialize weights to random values
      Random rand = RandomUtils.getRNG(0xDECAF, 0xDA7A);
      _syn1 = MemoryManager.malloc4f(_parameters._vec_size * vocabSize);
      _syn0 = MemoryManager.malloc4f(_parameters._vec_size * vocabSize);
      for (int i = 0; i < _parameters._vec_size * vocabSize; i++) _syn0[i] = (rand.nextFloat() - 0.5f) / _parameters._vec_size;
    }

    public static Word2VecModelInfo createInitialModelInfo(Word2VecParameters params) {
      Vec v = params.trainVec();
      WordCounts wordCounts = new WordCounts(new WordCountTask().doAll(v)._counts);
      return new Word2VecModelInfo(params, wordCounts);
    }

    private static <T extends Keyed> Key<T> publish(Keyed<T> keyed) {
      Scope.track_generic(keyed);
      DKV.put(keyed);
      return keyed._key;
    }

  }

  // wraps Vocabulary map into a Keyed object
  public static class Vocabulary extends Keyed<Vocabulary> {
    IcedHashMapGeneric<BufferedString, Integer> _data;
    Vocabulary(IcedHashMapGeneric<BufferedString, Integer> data) {
      super(Key.<Vocabulary>make());
      _data = data;
    }
  }

  // wraps Word-Count map into a Keyed object
  public static class WordCounts extends Keyed<WordCounts> {
    IcedHashMap<BufferedString, IcedLong> _data;
    WordCounts(IcedHashMap<BufferedString, IcedLong> data) {
      super(Key.<WordCounts>make());
      _data = data;
    }
  }

}
