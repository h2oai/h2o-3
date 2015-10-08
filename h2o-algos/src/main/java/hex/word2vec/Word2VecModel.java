package hex.word2vec;

import hex.ModelCategory;
import hex.ModelMetrics;
import water.Key;
import water.H2O;
import water.Futures;
import water.DKV;
import water.Iced;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.AppendableVec;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashMap;
import water.parser.BufferedString;
import water.util.ArrayUtils;
import water.util.Log;

import hex.Model;
import hex.word2vec.Word2VecModel.*;
import water.util.RandomUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

public class Word2VecModel extends Model<Word2VecModel, Word2VecParameters, Word2VecOutput> {
  private volatile Word2VecModelInfo _modelInfo;
  void setModelInfo(Word2VecModelInfo mi) { _modelInfo = mi; }
  final public Word2VecModelInfo getModelInfo() { return _modelInfo; }
  private Key _w2vKey;

  public Word2VecModel(Key selfKey, Word2VecParameters params, Word2VecOutput output) {
    super(selfKey, params, output);
    _modelInfo = new Word2VecModelInfo(params);
    assert(Arrays.equals(_key._kb, selfKey._kb));
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No Model Metrics for Word2Vec.");
  }

  @Override public double[] score0(Chunk[] cs, int foo, double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    throw H2O.unimpl();
  }
  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
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
    NonBlockingHashMap<BufferedString, Integer> vocabHM = buildVocabHashMap();
    Vec[] vs = ((Frame) _w2vKey.get()).vecs();
    BufferedString tmp = new BufferedString(target);
    return transform(tmp, vocabHM, vs);
  }

  private float[] transform(BufferedString tmp, NonBlockingHashMap<BufferedString, Integer> vocabHM, Vec[] vs) {
    final int vecSize = vs.length-1;
    float[] vec = new float[vecSize];
    if (!vocabHM.containsKey(tmp)) {
      Log.warn("Target word " + tmp + " isn't in vocabulary.");
      return null;
    }
    int row = vocabHM.get(tmp);
    for(int i=0; i < vecSize; i++) vec[i] = (float) vs[i+1].at(row);
    return vec;
  }

  /**
   * Find synonyms (i.e. wordvectors with the
   * highest cosine similarity) of the supplied
   * String and print them to stdout.
   *
   * @param target String of desired word
   * @param cnt Number of synonyms to find
   */
  public HashMap<String, Float> findSynonyms(String target, int cnt) {
    if (cnt > 0) {
      NonBlockingHashMap<BufferedString, Integer> vocabHM = buildVocabHashMap();
      Vec[] vs = ((Frame) _w2vKey.get()).vecs();
      BufferedString tmp = new BufferedString(target);
      float[] tarVec = transform(tmp, vocabHM, vs);
      return findSynonyms(tarVec, cnt, vs);
    } else {
      Log.err("Synonym count must be greater than 0.");
      return null;
    }
  }

  /**
   * Find synonyms (i.e. wordvectors with the
   * highest cosine similarity) of the word vector
   * for a word.
   *
   * @param tarVec word vector of a word
   * @param cnt number of synonyms to find
   *
   */
  public void findSynonyms(float[] tarVec, int cnt) {
    if (cnt > 0) {
      Vec[] vs = ((Frame) _w2vKey.get()).vecs();
      findSynonyms(tarVec, cnt, vs);
    } else Log.err("Synonym count must be greater than 0.");
  }

  private HashMap<String, Float> findSynonyms(float[] tarVec, int cnt, Vec[] vs) {
    final int vecSize= vs.length - 1, vocabSize = (int) vs[0].length();
    int[] matches = new int[cnt];
    float [] scores = new float[cnt];
    float[] curVec = new float[vecSize];

    HashMap<String, Float> res = new HashMap<>();

    if (tarVec.length != vs.length-1) {
      Log.warn("Target vector length differs from the vocab's vector length.");
      return null;
    }

    for (int i=0; i < vocabSize; i++) {
      for(int j=0; j < vecSize; j++) curVec[j] = (float) vs[j+1].at(i);
      float score = cosineSimilarity(tarVec, curVec);

      for (int j = 0; j < cnt; j++) {
        if (score > scores[j] && score < 0.999999) {
          for (int k = cnt - 1; k > j; k--) {
            scores[k] = scores[k - 1];
            matches[k] = matches[k-1];
          }
          scores[j] = score;
          matches[j] = i;
          break;
        }
      }
    }
    for (int i=0; i < cnt; i++)
      res.put(vs[0].atStr(new BufferedString(), matches[i]).toString(), scores[i]);

    return res;
  }
  /**
   * Basic calculation of cosine similarity
   * @param target - a word vector
   * @param current - a word vector
   * @return cosine similarity between the two word vectors
   */
  public float cosineSimilarity(float[] target, float[] current) {
    float dotProd = 0, tsqr = 0, csqr = 0;
    for(int i=0; i< target.length; i++) {
      dotProd += target[i] * current[i];
      tsqr += Math.pow(target[i],2);
      csqr += Math.pow(current[i],2);
    }
    return (float) (dotProd / (Math.sqrt(tsqr)*Math.sqrt(csqr)));
  }

  /**
   * Hashmap for quick lookup of a word's row number.
   */
  private  NonBlockingHashMap<BufferedString, Integer>  buildVocabHashMap() {
    NonBlockingHashMap<BufferedString, Integer> vocabHM;
    Vec word = ((Frame) _w2vKey.get()).vec(0);
    final int vocabSize = (int) ((Frame) _w2vKey.get()).numRows();
    vocabHM = new NonBlockingHashMap<>(vocabSize);
    for(int i=0; i < vocabSize; i++) vocabHM.put(word.atStr(new BufferedString(),i),i);
    return vocabHM;
  }

  public void buildModelOutput() {
    final int vecSize = _parms._vecSize;
    Futures fs = new Futures();
    String[] colNames = new String[vecSize];
    Vec[] vecs = new Vec[vecSize];
    Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(vecs.length);

    //allocate
    NewChunk cs[] = new NewChunk[vecs.length];
    AppendableVec avs[] = new AppendableVec[vecs.length];
    for (int i = 0; i < vecs.length; i++) {
      avs[i] = new AppendableVec(keys[i]);
      cs[i] = new NewChunk(avs[i], 0);
    }
    //fill in vector values
    for( int i = 0; i < _modelInfo._vocabSize; i++ ) {
      for (int j=0;  j < vecSize; j++) {
        cs[j].addNum(_modelInfo._syn0[i * vecSize + j]);
      }
    }

    //finalize vectors
    for (int i = 0; i < vecs.length; i++) {
      colNames[i] = new String("V"+i);
      cs[i].close(0, fs);
      vecs[i] = avs[i].close(fs);
    }

    fs.blockForPending();
    Frame fr = new Frame(_w2vKey = Key.make("w2v"));
    //FIXME this ties the word count frame to this one which means
    //FIXME one can't be deleted without destroying the other
    fr.add("Word", (_parms._vocabKey.get()).vec(0));
    fr.add(colNames, vecs);
    DKV.put(_w2vKey, fr);
  }

  @Override public void delete() {
    _parms._vocabKey.remove();
    _w2vKey.remove();
    remove();
    super.delete();
  }

  public static class Word2VecParameters extends Model.Parameters {
    static final int MAX_VEC_SIZE = 10000;

    public Word2Vec.WordModel _wordModel = Word2Vec.WordModel.SkipGram;
    public Word2Vec.NormModel _normModel = Word2Vec.NormModel.HSM;
    public Key<Frame> _vocabKey;
    public int _minWordFreq = 5;
    public int _vecSize = 100;
    public int _windowSize = 5;
    public int _epochs = 5;
    public int _negSampleCnt = 5;
    public float _initLearningRate = 0.05f;
    public float _sentSampleRate = 1e-3f;
  }

  public static class Word2VecOutput extends Model.Output{
    public Word2Vec.WordModel _wordModel;
    public Word2Vec.NormModel _normModel;
    public int _minWordFreq, _vecSize, _windowSize, _epochs, _negSampleCnt;
    public float _initLearningRate, _sentSampleRate;
    public Word2VecOutput(Word2Vec b) { super(b);}

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.Unknown;
    }
  }

  public static class Word2VecModelInfo extends Iced {
    static final int UNIGRAM_TABLE_SIZE = 10000000;
    static final float UNIGRAM_POWER = 0.75F;
    static final int MAX_CODE_LENGTH = 40;

    long _trainFrameSize;
    int _vocabSize;
    float _curLearningRate;
    float[] _syn0, _syn1;
    int[] _uniTable = null;
    int[][] _HBWTCode = null;
    int[][] _HBWTPoint = null;

    private Word2VecParameters _parameters;
    public final Word2VecParameters getParams() { return _parameters; }

    public Word2VecModelInfo() {}

    public Word2VecModelInfo(final Word2VecParameters params) {
      _parameters = params;

      if(_parameters._vocabKey == null) {
        _parameters._vocabKey = (new WordCountTask(_parameters._minWordFreq)).doAll(_parameters.train())._wordCountKey;
      }
      _vocabSize = (int) (_parameters._vocabKey.get()).numRows();
      _trainFrameSize = getTrainFrameSize(_parameters.train());

      //initialize weights to random values
      Random rand = RandomUtils.getRNG(0xDECAF, 0xDA7A);
      _syn1 = new float[_parameters._vecSize * _vocabSize];
      _syn0 = new float[_parameters._vecSize * _vocabSize];
      for (int i = 0; i < _parameters._vecSize * _vocabSize; i++) _syn0[i] = (rand.nextFloat() - 0.5f) / _parameters._vecSize;

      if(_parameters._normModel == Word2Vec.NormModel.HSM)
        buildHuffmanBinaryWordTree();
      else // NegSampling
        buildUnigramTable();
    }

    /**
     * Set of functions to accumulate counts of how many
     * words were processed so far.
     */
    private static int _localWordCnt=0, _globalWordCnt=0;
    public synchronized void addLocallyProcessed(long p) { _localWordCnt += p; }
    public synchronized long getLocallyProcessed() { return _localWordCnt; }
    public synchronized void setLocallyProcessed(int p) { _localWordCnt = p; }
    public synchronized void addGloballyProcessed(long p) { _globalWordCnt += p; }
    public synchronized long getGloballyProcessed() { return _globalWordCnt; }
    public synchronized long getTotalProcessed() { return _globalWordCnt + _localWordCnt; }

    /**
     * Used to add together the weight vectors between
     * two map instances.
     *
     * @param other - parameters object from other map method
     */
    protected void add(Word2VecModelInfo other) {
      ArrayUtils.add(_syn0, other._syn0);
      ArrayUtils.add(_syn1, other._syn1);
      addLocallyProcessed(other.getLocallyProcessed());
    }

    /**
     * Used to reduce the summations from map methods
     * to an average across map/reduce threads.
     *
     * @param N - number of map/reduce threads to divide by
     */
    protected void div(float N) {
      if (N > 1) {
        ArrayUtils.div(_syn0, N);
        ArrayUtils.div(_syn1, N);
      }
    }

    /**
     * Calculates a new global learning rate for the next round
     * of map/reduce calls.
     * The learning rate is a coefficient that controls the amount that
     * newly learned information affects current learned information.
     */
    public void updateLearningRate() {
      _curLearningRate = _parameters._initLearningRate * (1 - getTotalProcessed() / (float) (_parameters._epochs * _trainFrameSize + 1));
      if (_curLearningRate < _parameters._initLearningRate * 0.0001F) _curLearningRate = _parameters._initLearningRate * 0.0001F;
    }


    /**
     * Generates a unigram table from the [word, count] vocab frame.
     * The unigram table is needed for normalizing through negative sampling.
     *
     * This design consumes memory for speed and simplicity.  It also breaks for
     * smaller vocabularies.  Alternates should be explored.
     */
    private void buildUnigramTable() {
      float d = 0;
      long vocabWordsPow = 0;
      _uniTable = new int[UNIGRAM_TABLE_SIZE];

      Vec wCount = (_parameters._vocabKey.get()).vec(1);
      for (int i=0; i < wCount.length(); i++) vocabWordsPow += Math.pow(wCount.at8(i), UNIGRAM_POWER);
      for (int i = 0, j =0; i < UNIGRAM_TABLE_SIZE; i++) {
        _uniTable[i] = j;
        if (j >= _vocabSize-1) j = 0;
        if (i / (float) UNIGRAM_TABLE_SIZE > d)
          d += Math.pow(wCount.at8(j++), UNIGRAM_POWER) / (float) vocabWordsPow;
      }
    }

/*  Explored packing the unigram table into chunks for the benefit of
   compression.  The random access nature ended up increasing the run
   time of a negative sampling run by ~50%.

  private Key buildUnigramTable() {
    Futures fs = new Futures();
    Vec wCount, uniTblVec;
    AppendableVec utAV = new AppendableVec(Vec.newKey());
    NewChunk utNC = null;
    long vocabWordsPow = 0;
    float d = 0;
    int chkIdx = 0;

    wCount = ((Frame)_vocabKey.get()).vec(1);
    for (int i=0; i < wCount.length(); i++) vocabWordsPow += Math.pow(wCount.at8(i), UNIGRAM_POWER);
    for (int i = 0, j =0; i < UNIGRAM_TABLE_SIZE; i++) {
      //allocate as needed
      if ((i % Vec.CHUNK_SZ) == 0){
        if (utNC != null) utNC.close(chkIdx++, fs);
        utNC = new NewChunk(utAV, chkIdx);
      }

      utNC.addNum(j, 0);
      if (i / (float) UNIGRAM_TABLE_SIZE > d) {
        d += Math.pow(wCount.at8(++j), UNIGRAM_POWER) / (float) vocabWordsPow;
      }
      if (j >= _vocabSize) j = _vocabSize - 1;
    }

    //finalize vectors
    utNC.close(chkIdx, fs);
    uniTblVec = utAV.close(fs);
    fs.blockForPending();

    return uniTblVec._key;
  } */


    /**
     * Generates the values for a Huffman binary tree
     * from the [word, count] vocab frame.
     */
    private void buildHuffmanBinaryWordTree() {
      int min1i, min2i, pos1, pos2;
      int[] point = new int[MAX_CODE_LENGTH];
      int[] code = new int[MAX_CODE_LENGTH];
      long[] count = new long[_vocabSize * 2 - 1];
      int[] binary = new int[_vocabSize * 2 - 1];
      int[] parent_node = new int[_vocabSize * 2 - 1];
      Vec wCount = (_parameters._vocabKey.get()).vec(1);
      _HBWTCode = new int[_vocabSize][];
      _HBWTPoint = new int[_vocabSize][];

      assert (_vocabSize == wCount.length());
      for (int i = 0; i < _vocabSize; i++) count[i] = wCount.at8(i);
      for (int i = _vocabSize; i < _vocabSize * 2 - 1; i++) count[i] = (long) 1e15;
      pos1 = _vocabSize - 1;
      pos2 = _vocabSize;

      // Following algorithm constructs the Huffman tree by adding one node at a time
      for (int i = 0; i < _vocabSize - 1; i++) {
        // First, find two smallest nodes 'min1, min2'
        if (pos1 >= 0) {
          if (count[pos1] < count[pos2]) {
            min1i = pos1;
            pos1--;
          } else {
            min1i = pos2;
            pos2++;
          }
        } else {
          min1i = pos2;
          pos2++;
        }
        if (pos1 >= 0) {
          if (count[pos1] < count[pos2]) {
            min2i = pos1;
            pos1--;
          } else {
            min2i = pos2;
            pos2++;
          }
        } else {
          min2i = pos2;
          pos2++;
        }
        count[_vocabSize + i] = count[min1i] + count[min2i];
        parent_node[min1i] = _vocabSize + i;
        parent_node[min2i] = _vocabSize + i;
        binary[min2i] = 1;
      }
      // Now assign binary code to each vocabulary word
      for (int j = 0; j < _vocabSize; j++) {
        int k = j;
        int m = 0;
        while (true) {
          int val = binary[k];
          code[m] = val;
          point[m] = k;
          m++;
          k = parent_node[k];
          if (k == 0) break;
        }
        _HBWTCode[j] = new int[m];
        _HBWTPoint[j] = new int[m + 1];
        _HBWTPoint[j][0] = _vocabSize - 2;
        for (int l = 0; l < m; l++) {
          _HBWTCode[j][m - l - 1] = code[l];
          _HBWTPoint[j][m - l] = point[l] - _vocabSize;
        }
      }
    }

    /**
     * Calculates the number of words that Word2Vec will train on.
     * This is a needed parameter for correct trimming of the learning
     * rate in the algo.  Rather that require the user to calculate it,
     * this finds it and adds it to the parameters object.
     *
     * @param tf - frame containing words to train on
     * @return count - total words in training frame
     */
    private long getTrainFrameSize(Frame tf) {
      long count=0;

      for (Vec v: tf.vecs()) if(v.isString()) count += v.length();

      return count;
    }
  }
}
