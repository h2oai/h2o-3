package hex.word2vec;

import water.DKV;
import water.Job;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.parser.BufferedString;
import hex.word2vec.Word2VecModel.*;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.IcedHashMapGeneric;
import water.util.IcedLong;

import java.util.Iterator;

public class WordVectorTrainer extends MRTask<WordVectorTrainer> {
  private static final int MAX_SENTENCE_LEN = 1000;
  private static final int EXP_TABLE_SIZE = 1000;
  private static final int MAX_EXP = 6;
  private static final float[] _expTable = calcExpTable();
  private static final float LEARNING_RATE_MIN_FACTOR = 0.0001F; // learning rate stops decreasing at (initLearningRate * this factor)

  // Job
  private final Job<Word2VecModel> _job;

  // Params
  private final Word2Vec.WordModel _wordModel;
  private final int _wordVecSize, _windowSize, _epochs;
  private final float _initLearningRate;
  private final float _sentSampleRate;
  private final long _vocabWordCount;

  // Model IN
  private final Key<Vocabulary> _vocabKey;
  private final Key<WordCounts> _wordCountsKey;
  private final Key<HBWTree> _treeKey;
  private final long _prevTotalProcessedWords;

  // Model IN & OUT
  // _syn0 represents the matrix of synaptic weights connecting the input layer of the NN to the hidden layer,
  // similarly _syn1 corresponds to the weight matrix of the synapses connecting the hidden layer to the output layer
  // both matrices are represented in a 1D array, where M[i,j] == array[i * VEC_SIZE + j]
  float[] _syn0, _syn1;
  long _processedWords = 0L;

  // Node-Local (Shared)
  IcedLong _nodeProcessedWords; // mutable long, approximates the total number of words processed by this node
  private transient IcedHashMapGeneric<BufferedString, Integer> _vocab;
  private transient IcedHashMap<BufferedString, IcedLong> _wordCounts;
  private transient int[][] _HBWTCode;
  private transient int[][] _HBWTPoint;

  private float _curLearningRate;
  private long _seed = System.nanoTime();

  public WordVectorTrainer(Job<Word2VecModel> job, Word2VecModelInfo input) {
    super(null);
    _job = job;

    _treeKey = input._treeKey;
    _vocabKey = input._vocabKey;
    _wordCountsKey = input._wordCountsKey;

    // Params
    _wordModel = input.getParams()._word_model;
    _wordVecSize = input.getParams()._vec_size;
    _windowSize = input.getParams()._window_size;
    _sentSampleRate = input.getParams()._sent_sample_rate;
    _epochs = input.getParams()._epochs;
    _initLearningRate = input.getParams()._init_learning_rate;

    _vocabWordCount = input._vocabWordCount;
    _prevTotalProcessedWords = input._totalProcessedWords;

    _syn0 = input._syn0;
    _syn1 = input._syn1;
    _curLearningRate = calcLearningRate(_initLearningRate, _epochs, _prevTotalProcessedWords, _vocabWordCount);
  }

  @Override
  protected void setupLocal() {
    _vocab = ((Vocabulary) DKV.getGet(_vocabKey))._data;
    _wordCounts = ((WordCounts) DKV.getGet(_wordCountsKey))._data;
    HBWTree t = DKV.getGet(_treeKey);
    _HBWTCode = t._code;
    _HBWTPoint = t._point;
    _nodeProcessedWords = new IcedLong(0L);
  }

  // Precompute the exp() table
  private static float[] calcExpTable() {
    float[] expTable = new float[EXP_TABLE_SIZE];
    for (int i = 0; i < EXP_TABLE_SIZE; i++) {
      expTable[i] = (float) Math.exp((i / (float) EXP_TABLE_SIZE * 2 - 1) * MAX_EXP);
      expTable[i] = expTable[i] / (expTable[i] + 1);  // Precompute f(x) = x / (x + 1)
    }
    return expTable;
  }

  @Override public void map(Chunk chk) {
    final int winSize = _windowSize, vecSize = _wordVecSize;
    float[] neu1 = new float[vecSize];
    float[] neu1e = new float[vecSize];
    ChunkSentenceIterator sentIter = new ChunkSentenceIterator(chk);

    int wordCount = 0;
    while (sentIter.hasNext()) {
      int sentLen = sentIter.nextLength();
      int[] sentence = sentIter.next();
      for (int sentIdx = 0; sentIdx < sentLen; sentIdx++) {
        int curWord = sentence[sentIdx];
        int bagSize = 0;
        if (_wordModel == Word2Vec.WordModel.CBOW) {
          for (int j = 0; j < vecSize; j++) neu1[j] = 0;
          for (int j = 0; j < vecSize; j++) neu1e[j] = 0;
        }
        
        // for each item in the window (except curWord), update neu1 vals
        int winSizeMod = cheapRandInt(winSize);
        for (int winIdx = winSizeMod; winIdx < winSize * 2 + 1 - winSizeMod; winIdx++) {
          if (winIdx != winSize) { // skips curWord in sentence
            int winWordSentIdx = sentIdx - winSize + winIdx;
            if (winWordSentIdx < 0 || winWordSentIdx >= sentLen) continue;
            int winWord = sentence[winWordSentIdx];
            if (_wordModel == Word2Vec.WordModel.SkipGram)
              skipGram(curWord, winWord, neu1e);
            else { // CBOW
              for (int j = 0; j < vecSize; j++) neu1[j] += _syn0[j + winWord * vecSize];
              bagSize++;
            }
          }
        } // end for each item in the window
        if (_wordModel == Word2Vec.WordModel.CBOW && bagSize > 0) {
          CBOW(curWord, sentence, sentIdx, sentLen, winSizeMod, bagSize, neu1, neu1e);
        }

        wordCount++;
        // update learning rate
        if (wordCount % 10000 == 0) {
          _nodeProcessedWords._val += 10000;
          long totalProcessedWordsEst = _prevTotalProcessedWords + _nodeProcessedWords._val;
          _curLearningRate = calcLearningRate(_initLearningRate, _epochs, totalProcessedWordsEst, _vocabWordCount);
        }
      } // for each item in the sentence
    } // while more sentences
    _processedWords = wordCount;
    _nodeProcessedWords._val += wordCount % 10000;
    _job.update(1);
  }

  @Override public void reduce(WordVectorTrainer other) {
    _processedWords += other._processedWords;
    if (_syn0 != other._syn0) { // other task worked on a different syn0
      float c = (float) other._processedWords / _processedWords;
      ArrayUtils.add(1.0f - c, _syn0, c, other._syn0);
      ArrayUtils.add(1.0f - c, _syn1, c, other._syn1);
      // for diagnostics only
      _nodeProcessedWords._val += other._nodeProcessedWords._val;
    }
  }

  private void skipGram(int curWord, int winWord, float[] neu1e) {
    final int vecSize = _wordVecSize;
    final int l1 = winWord * vecSize;
    for (int i = 0; i < vecSize; i++) neu1e[i] = 0;

    hierarchicalSoftmaxSG(curWord, l1, neu1e);

    // Learned weights input -> hidden
    for (int i = 0; i < vecSize; i++) _syn0[i + l1] += neu1e[i];
  }

  private void hierarchicalSoftmaxSG(final int targetWord, final int l1, float[] neu1e) {
    final int vecSize = _wordVecSize, tWrdCodeLen = _HBWTCode[targetWord].length;
    final float alpha = _curLearningRate;

    for (int i = 0; i < tWrdCodeLen; i++) {
      int l2 = _HBWTPoint[targetWord][i] * vecSize;

      float f = 0;
      // Propagate hidden -> output (calc sigmoid)
      for (int j = 0; j < vecSize; j++) f += _syn0[j + l1] * _syn1[j + l2];

      if (f <= -MAX_EXP) continue;
      else if (f >= MAX_EXP) continue;
      else f = _expTable[(int) ((f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2))];

      float gradient = (1 - _HBWTCode[targetWord][i] - f) * alpha;
      // Propagate errors output -> hidden
      for (int j = 0; j < vecSize; j++) neu1e[j] += gradient * _syn1[j + l2];
      // Learn weights hidden -> output
      for (int j = 0; j < vecSize; j++) _syn1[j + l2] += gradient * _syn0[j + l1];
    }
  }

  private void CBOW(
      int curWord,
      int[] sentence,
      int sentIdx,
      int sentLen,
      int winSizeMod,
      int bagSize,
      float[] neu1,
      float[] neu1e
  ) {
    int winWordSentIdx, winWord;
    final int vecSize = _wordVecSize, winSize = _windowSize;
    final int curWinSize = winSize * 2 + 1 - winSize;

    for (int i = 0; i < vecSize; i++) neu1[i] /= bagSize;
    hierarchicalSoftmaxCBOW(curWord, neu1, neu1e);

    // hidden -> in
    for (int winIdx = winSizeMod; winIdx < curWinSize; winIdx++) {
      if (winIdx != winSize) {
        winWordSentIdx = sentIdx - winSize + winIdx;
        if (winWordSentIdx < 0 || winWordSentIdx >= sentLen) continue;
        winWord = sentence[winWordSentIdx];
        for (int i = 0; i < vecSize; i++) _syn0[i + winWord * vecSize] += neu1e[i];
      }
    }
  }

  private void hierarchicalSoftmaxCBOW(final int targetWord, float[] neu1, float[] neu1e) {
    final int vecSize = _wordVecSize, tWrdCodeLen = _HBWTCode[targetWord].length;
    final float alpha = _curLearningRate;
    float gradient, f = 0;
    int l2;

    for (int i = 0; i < tWrdCodeLen; i++, f = 0) {
      l2 = _HBWTPoint[targetWord][i] * vecSize;

      // Propagate hidden -> output (calc sigmoid)
      for (int j = 0; j < vecSize; j++) f += neu1[j] * _syn1[j + l2];

      if (f <= -MAX_EXP) continue;
      else if (f >= MAX_EXP) continue;
      else f = _expTable[(int) ((f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2))];

      gradient = (1 - _HBWTCode[targetWord][i] - f) * alpha;
      // Propagate errors output -> hidden
      for (int j = 0; j < vecSize; j++) neu1e[j] += gradient * _syn1[j + l2];
      // Learn weights hidden -> output
      for (int j = 0; j < vecSize; j++) _syn1[j + l2] += gradient * neu1[j];
    }
  }

  /**
   * Calculates a new global learning rate for the next round
   * of map/reduce calls.
   * The learning rate is a coefficient that controls the amount that
   * newly learned information affects current learned information.
   */
  private static float calcLearningRate(float initLearningRate, int epochs, long totalProcessed, long vocabWordCount) {
    float rate = initLearningRate * (1 - totalProcessed / (float) (epochs * vocabWordCount + 1));
    if (rate < initLearningRate * LEARNING_RATE_MIN_FACTOR) rate = initLearningRate * LEARNING_RATE_MIN_FACTOR;
    return rate;
  }

  public void updateModelInfo(Word2VecModelInfo modelInfo) {
    modelInfo._syn0 = _syn0;
    modelInfo._syn1 = _syn1;
    modelInfo._totalProcessedWords += _processedWords;
  }

  /**
    * This is cheap and moderate in quality.
    *
    * @param max - Upper range limit.
    * @return int between 0-(max-1).
    */
  private int cheapRandInt(int max) {
    _seed ^= ( _seed << 21);
    _seed ^= ( _seed >>> 35);
    _seed ^= ( _seed << 4);
    int r = (int) _seed % max;
    return r > 0 ? r : -r;
  }

  private class ChunkSentenceIterator implements Iterator<int[]> {

    private Chunk _chk;
    private int _pos = 0;

    private int _len = -1;
    private int[] _sent = new int[MAX_SENTENCE_LEN + 1];

    private ChunkSentenceIterator(Chunk chk) { _chk = chk; }

    @Override
    public boolean hasNext() {
      return nextLength() >= 0;
    }

    private int nextLength() {
      if (_len >= 0)
        return _len;
      if (_pos >= _chk._len)
        return -1;
      _len = 0;
      BufferedString tmp = new BufferedString();
      for (; _pos < _chk._len && ! _chk.isNA(_pos) && _len < MAX_SENTENCE_LEN; _pos++) {
        BufferedString str = _chk.atStr(tmp, _pos);
        if (! _vocab.containsKey(str)) continue; // not in the vocab, skip
        if (_sentSampleRate > 0) {  // sub-sampling while creating a sentence
          long count = _wordCounts.get(str)._val;
          float ran = (float) ((Math.sqrt(count / (_sentSampleRate * _vocabWordCount)) + 1) * (_sentSampleRate * _vocabWordCount) / count);
          if (ran * 65536 < cheapRandInt(0xFFFF)) continue;
        }
        _sent[_len++] = _vocab.get(tmp);
      }
      _sent[_len] = -1;
      _pos++;
      return _len;
    }

    @Override
    public int[] next() {
      if (hasNext()) {
        _len = -1;
        return _sent;
      }
      else
        return null;
    }

    @Override
    public void remove() { throw new UnsupportedOperationException("Remove is not supported"); } // should never be called
  }

}
