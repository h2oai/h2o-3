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
    BufferedString word = new BufferedString(target);
    if (! _output._vocab.containsKey(word))
      return null;
    int wordIdx = _output._vocab.get(word);
    return Arrays.copyOfRange(_output._vecs, wordIdx * _output._vecSize, (wordIdx + 1) * _output._vecSize);
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
    final int vecSize = _parms._vecSize;

    IcedHashMapGeneric<BufferedString, Integer> vocab = ((Vocabulary) DKV.getGet(modelInfo._vocabKey))._data;
    BufferedString[] words = new BufferedString[vocab.size()];
    for (BufferedString str : vocab.keySet())
      words[vocab.get(str)] = str;

    _output._vecSize = vecSize;
    _output._vecs = modelInfo._syn0;
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
    @Override public long progressUnits() { return _epochs; }
    static final int MAX_VEC_SIZE = 10000;

    public Word2Vec.WordModel _wordModel = Word2Vec.WordModel.SkipGram;
    public Word2Vec.NormModel _normModel = Word2Vec.NormModel.HSM;
    public int _minWordFreq = 5;
    public int _vecSize = 100;
    public int _windowSize = 5;
    public int _epochs = 5;
    public float _initLearningRate = 0.05f;
    public float _sentSampleRate = 1e-3f;
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
      return ModelCategory.Unknown;
    }
  }

  public static class Word2VecModelInfo extends Iced {

    long _vocabWordCount;
    long _totalProcessedWords = 0L;

    float[] _syn0, _syn1;
    Key<HBWTree> _treeKey;
    Key<Vocabulary> _vocabKey;
    Key<?> _wordCountsKey;

    private Word2VecParameters _parameters;
    public final Word2VecParameters getParams() { return _parameters; }

    public Word2VecModelInfo() {}

    private Word2VecModelInfo(Word2VecParameters params, WordCounts wordCounts) {
      _parameters = params;

      long vocabWordCount = 0L;
      List<Map.Entry<BufferedString, IcedLong>> wordCountList = new ArrayList<>(wordCounts._data.size());
      for (Map.Entry<BufferedString, IcedLong> wc : wordCounts._data.entrySet()) {
        if (wc.getValue()._val >= _parameters._minWordFreq) {
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
      _syn1 = MemoryManager.malloc4f(_parameters._vecSize * vocabSize);
      _syn0 = MemoryManager.malloc4f(_parameters._vecSize * vocabSize);
      for (int i = 0; i < _parameters._vecSize * vocabSize; i++) _syn0[i] = (rand.nextFloat() - 0.5f) / _parameters._vecSize;
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
