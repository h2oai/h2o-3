package water.string;

import water.Iced;
import water.Key;
import water.fvec.Vec;
import water.util.Log;
import water.util.ArrayUtils;
import water.fvec.Frame;
import java.util.Random;

/**
 * Parameters needed to run Word2Vec. Checks
 * parameter validity upon creation. Creates
 * necessary derived values (UnigramTable, or
 * Huffman binary word tree).  Parameters
 * get passed to and from WordVectorTrainer
 * for each epoch.  This mechanism is used to
 * consolidate and update results between task
 * in between each epoch.
 */
public class Word2VecParameters extends Iced {
  public enum WordModel { SkipGram, CBOW }
  public enum NormModel { HSM, NegSampling }
  static final int MAX_VEC_SIZE = 10000;
  static final int UNIGRAM_TABLE_SIZE = 100000000;
  static final float UNIGRAM_POWER = 0.75F;
  static final int MAX_CODE_LENGTH = 40;

  WordModel _wordModel;
  NormModel _normModel;
  Key _vocabKey;
  int _vocabSize, _vecSize, _windowSize, _epochs, _numNegEx;
  long _trainFrameSize;
  float _curLearningRate, _initLearningRate;
  float _sentSampleRate;
  float[] _syn0, _syn1;
  boolean _valid = false;
  int[] _uniTable = null;
  int[][] _HBWTCode = null;
  int[][] _HBWTPoint = null;

  /**
   * Constructor used for specifying the number of negative sampling cases.
   * @param wordModel - SkipGram or CBOW
   * @param normModel - Hierarchical softmax or Negative sampling
   * @param numNegEx - Number of negative samples used per word
   * @param vocabKey - Key pointing to frame of [Word, Cnt] vectors
   * @param vecSize - Size of word vectors
   * @param winSize - Size of word window
   * @param sentSampleRate - Sampling rate in sentences to generate new n-grams
   * @param learningRate - Starting alpha value.  This tempers the effect of progressive information as learning progresses.
   * @param epochs - Number of iterations data is run through.
   */
  public Word2VecParameters(WordModel wordModel, NormModel normModel, int numNegEx, Key vocabKey, int vecSize, int winSize, float sentSampleRate, float learningRate, int epochs) {
    this(vocabKey, vecSize, winSize, sentSampleRate, learningRate, epochs);
    if (numNegEx < 0) {
      Log.err("Negative value for the number of negative samples not allowed for Word2Vec.  Expected value > 0, received " + numNegEx);
      _valid = false;
    } else if (normModel == NormModel.HSM && numNegEx != 0) {
      Log.err("Both hierarchical softmax and negative samples != 0 is not allowed for Word2Vec.  Expected value = 0, received " + numNegEx);
      _valid = false;
    } else if (_valid) {
      _wordModel = wordModel;
      _normModel = normModel;
      _numNegEx = numNegEx;
      buildUnigramTable();
    }
  }

  /**
   * Constructor used for hierarchical softmax cases.
   * @param wordModel - SkipGram or CBOW
   * @param vocabKey - Key pointing to frame of [Word, Cnt] vectors
   * @param vecSize - Size of word vectors
   * @param winSize - Size of word window
   * @param sentSampleRate - Sampling rate in sentences to generate new n-grams
   * @param learningRate - Starting alpha value.  This tempers the effect of progressive information as learning progresses.
   * @param epochs - Number of iterations data is run through.
   */
  public Word2VecParameters(WordModel wordModel, NormModel normModel, Key vocabKey, int vecSize, int winSize, float sentSampleRate, float learningRate, int epochs) {
    this(vocabKey, vecSize, winSize, sentSampleRate, learningRate, epochs);
    if (_valid) {
      _wordModel = wordModel;
      _normModel = normModel;
      if (normModel == NormModel.NegSampling) {
        buildUnigramTable();
      } else {  //HSM
        buildHuffmanBinaryWordTree();
      }

    }
  }

  /**
   * Internal constructor used for checking and initializing common values.
   * @param vocabKey - Key pointing to frame of [Word, Cnt] vectors
   * @param vecSize - Size of word vectors
   * @param winSize - Size of word window
   * @param sentSampleRate - Sampling rate in sentences to generate new n-grams
   * @param learningRate - Starting alpha value.  This tempers the effect of progressive information as learning progresses.
   * @param epochs - Number of iterations data is run through.
   */
  private Word2VecParameters( Key vocabKey, int vecSize, int winSize, float sentSampleRate, float learningRate, int epochs) {
    if (_valid = sanityCheck(vocabKey, vecSize, winSize, sentSampleRate, learningRate, epochs)) {
      _vocabKey = vocabKey;
      _vecSize = vecSize;
      _windowSize = winSize;
      _sentSampleRate = sentSampleRate;
      _curLearningRate = learningRate;
      _initLearningRate = learningRate;
      _epochs = epochs;
      _vocabSize = (int) ((Frame) _vocabKey.get()).numRows();

      //initialize weights to random values
      Random rand = new Random();
      _syn1 = new float[vecSize * _vocabSize];
      _syn0 = new float[vecSize * _vocabSize];
      for (int i = 0; i < vecSize * _vocabSize; i++) _syn0[i] = (rand.nextFloat() - 0.5f) / vecSize;
    }
  }

  /**
   * Checks parameter values for "sane" values.  "Sane" is a mix
   * of values that make sense within the algorithms, as well as
   * within the limits of the H2O system.
   * @param vocabKey - Key pointing to frame of [Word, Cnt] vectors
   * @param vecSize - Size of word vectors
   * @param winSize - Size of word window
   * @param sentSampleRate - Sampling rate in sentences to generate new n-grams
   * @param learningRate - Starting alpha value.  This tempers the effect of progressive information as learning progresses.
   * @param epochs - Number of iterations data is run through.
   * @return - returns TRUE if all parameters are "sane"
   */
  private boolean sanityCheck(Key vocabKey, int vecSize, int winSize, float sentSampleRate, float learningRate, int epochs) {
    Frame fr = vocabKey.get();
    if (fr == null) {
      Log.err("Key used for word count frame, pointed to no frame.");
      return false;
    }
    if (fr.numCols() != 2) {
      Log.err("Frame passed for the word count, contained the incorrect number of columns.  Expected 2, found "+fr.numCols());
      return false;
    }
    if (fr.numRows() > Integer.MAX_VALUE) {
      Log.err("Vocab size exceeds current limit of "+Integer.MAX_VALUE+".  Whew, that's a lot of words!  " +
              "Consider stemming, or tell developers to upgrade to handle larger vocabularies.");
      return false;
    }
    if (vecSize > MAX_VEC_SIZE) {
      _vecSize = MAX_VEC_SIZE;
      Log.warn("Requested vector size of "+vecSize+" in Word2Vec, exceeds limit of "+MAX_VEC_SIZE+".  Using " + MAX_VEC_SIZE + "instead.");
    }
    if (winSize < 1) {
      Log.err("Negative window size not allowed for Word2Vec.  Expected value > 0, received "+winSize);
      return false;
    }
    if (sentSampleRate < 0.0) {
      Log.err("Negative sentence sample rate not allowed for Word2Vec.  Expected a value > 0.0, received "+sentSampleRate);
      return false;
    }
    if (learningRate < 0.0) {
      Log.err("Negative learning rate not allowed for Word2Vec.  Expected a value > 0.0, received "+ learningRate);
      return false;
    }
    if (epochs < 1) {
      Log.err("Negative epoch count not allowed for Word2Vec.  Expected value > 0, received "+epochs);
      return false;
    }
    return true;
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
  protected void add(Word2VecParameters other) {
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
   * Generates a unigram table from the [word, count]
   * vocab frame.  The unigram table is needed for
   * normalizing through negative sampling.
   *
   * @return - returns a key to a vec holding the unigram table results.
   */
  private void buildUnigramTable() {
    float d = 0;
    long vocabWordsPow = 0;
    _uniTable = new int[UNIGRAM_TABLE_SIZE];

    Vec wCount = ((Frame)_vocabKey.get()).vec(1);
    for (int i=0; i < wCount.length(); i++) vocabWordsPow += Math.pow(wCount.at8(i), UNIGRAM_POWER);
    for (int i = 0, j =0; i < UNIGRAM_TABLE_SIZE; i++) {
      _uniTable[i] = j;
      if (i / (float) UNIGRAM_TABLE_SIZE > d) {
        d += Math.pow(wCount.at8(++j), UNIGRAM_POWER) / (float) vocabWordsPow;
      }
      if (j >= _vocabSize) j = _vocabSize - 1;
    }
  }

/*  Explored packing the unigram table into chunks for the benefit of
   compression.  The random access nature ended up increasing the run
   time of a negative sampling run by ~50%.

   Tests without using this as a giant lookup table don't seem to fair much better.

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
    int[] binary = new int[_vocabSize * 2 - 1 ];
    int[] parent_node = new int[_vocabSize * 2 - 1];
    Vec wCount = ((Frame)_vocabKey.get()).vec(1);
    _HBWTCode = new int[_vocabSize][];
    _HBWTPoint = new int[_vocabSize][];

    assert(_vocabSize == wCount.length());
    for (int i=0; i < _vocabSize; i++) count[i] = wCount.at8(i);
    for (int i = _vocabSize; i < _vocabSize * 2 - 1; i++) count[i] = (long) 1e15;
    pos1 = _vocabSize - 1; pos2 = _vocabSize;

    // Following algorithm constructs the Huffman tree by adding one node at a time
    for (int i = 0; i < _vocabSize-1; i++) {
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
      _HBWTPoint[j] = new int[m+1];
      _HBWTPoint[j][0] = _vocabSize - 2;
      for (int l = 0; l < m; l++) {
        _HBWTCode[j][m - l - 1] = code[l];
        _HBWTPoint[j][m - l] = point[l] - _vocabSize;
      }
    }
  }
}