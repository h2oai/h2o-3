package hex.word2vec;

import water.H2O;
import water.MRTask;
import water.fvec.CStrChunk;
import water.fvec.Vec;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.nbhm.NonBlockingHashMap;
import water.parser.BufferedString;
import water.util.Log;
import hex.word2vec.Word2VecModel.*;
import hex.word2vec.Word2Vec.*;
import java.util.Random;

public class WordVectorTrainer extends MRTask<WordVectorTrainer> {
  static final int MAX_SENTENCE_LEN = 1000;
  static final int MIN_SENTENCE_LEN = 10;
  static final int EXP_TABLE_SIZE = 1000;
  static final int MAX_EXP = 6;

  private Word2VecModelInfo _input;
  Word2VecModelInfo _output;
  Frame _vocab;
  static NonBlockingHashMap<BufferedString,Integer> _vocabHM;
  final WordModel _wordModel; final NormModel _normModel;
  final int _vocabSize, _wordVecSize, _windowSize, _epochs, _negExCnt;
  final float _initLearningRate, _sentSampleRate;
  static float[] _syn0, _syn1, _expTable;
  final int[]_unigramTable;
  final int[][] _HBWTCode;
  final int[][] _HBWTPoint;
  int _chunkNodeCount = 1;
  transient float _curLearningRate;
  transient int _chkIdx =0;
  transient Random _rand;
  static transient long _seed;

  public WordVectorTrainer( Word2VecModelInfo input) {
    super(null);
    _input=input;
    _wordModel = input.getParams()._wordModel;
    _normModel = input.getParams()._normModel;
    _vocab = input.getParams()._vocabKey.get();
    _vocabSize = (int)_vocab.numRows();
    _wordVecSize = input.getParams()._vecSize;
    _windowSize = input.getParams()._windowSize;
    _syn0 = input._syn0; _syn1 = input._syn1;
    _initLearningRate = input.getParams()._initLearningRate;
    _sentSampleRate = input.getParams()._sentSampleRate;
    _epochs = input.getParams()._epochs;
    _seed = System.nanoTime();
    assert(_output == null);
    assert(_vocab.numRows() > 0);

    if (input.getParams()._normModel == NormModel.NegSampling){
      _negExCnt = input.getParams()._negSampleCnt;
      _unigramTable = input._uniTable;
      _HBWTCode = null;
      _HBWTPoint = null;
    } else { //HSM
      _negExCnt = 0;
      _unigramTable = null;
      _HBWTCode = input._HBWTCode;
      _HBWTPoint = input._HBWTPoint;
    }
  }
  final public Word2VecModelInfo getModelInfo() { return _output; }

  @Override
  protected void setupLocal() {
    _syn0 = _input._syn0;  _syn1 = _input._syn1;
    _output = _input; //faster, good enough in this case (since the input was freshly deserialized by the Weaver)
    _input = null;
    _rand = new Random();
    initExpTable();
    buildVocabHashMap();
    _curLearningRate = _output._curLearningRate;
    _output.setLocallyProcessed(0);
  }


  private void buildVocabHashMap() {
    Vec word = _vocab.vec(0);
    _vocabHM = new NonBlockingHashMap<>((int)_vocab.numRows());
    for(int i=0; i < _vocab.numRows(); i++) _vocabHM.put(word.atStr(new BufferedString(),i),i);
  }

  private void updateAlpha(int localWordCnt) {
    _curLearningRate = _initLearningRate * (1 - (_output.getGloballyProcessed() + localWordCnt) / (float) (_epochs * _output._trainFrameSize + 1));
    if (_curLearningRate < _initLearningRate * 0.0001F) _curLearningRate = _initLearningRate * 0.0001F;
  }

  /*
   * All words in sentence should be in vocab
   */
  private int getSentence(int[] sentence, CStrChunk cs) {
    Vec count = _vocab.vec(1);
    BufferedString tmp = new BufferedString();
    float ran;
    int wIdx, sentIdx = 0;

    int sentLen = (cs._len - 1 - _chkIdx);
    if (sentLen >= MAX_SENTENCE_LEN) sentLen = MAX_SENTENCE_LEN;
    else if (sentLen < MIN_SENTENCE_LEN) return 0;

    for (; _chkIdx < cs._len; _chkIdx++) {
      cs.atStr(tmp, _chkIdx);
      if (!_vocabHM.containsKey(tmp)) continue; //not in vocab, skip
      wIdx = _vocabHM.get(tmp);
      if (_sentSampleRate > 0) {  // subsampling while creating a "_sentence"
        // paper says: float ran = 1 - sqrt(sample / (vocab[word].cn / (float)trainWords));
        ran = ((float) Math.sqrt(count.at8(wIdx) / (_sentSampleRate * _output._trainFrameSize)) + 1) * (_sentSampleRate * _output._trainFrameSize) / (float) count.at8(wIdx);
        // paper says: ran > ....
        if (ran < _rand.nextFloat()) continue;
      }
      sentence[sentIdx++] = wIdx;
      if (sentIdx >= sentLen) break;
    }

    return sentLen;
  }

  // Precompute the exp() table
  private void initExpTable() {
    _expTable = new float[EXP_TABLE_SIZE];

    for (int i = 0; i < EXP_TABLE_SIZE; i++) {
      _expTable[i] = (float) Math.exp((i / (float) EXP_TABLE_SIZE * 2 - 1) * MAX_EXP);
      _expTable[i] = _expTable[i] / (_expTable[i] + 1);  // Precompute f(x) = x / (x + 1)
    }
  }

  @Override public void map(Chunk cs[]) {
    int wrdCnt=0, bagSize=0, sentLen, curWord, winSizeMod;
    int winWordSentIdx, winWord;
    final int winSize = _windowSize, vecSize = _wordVecSize;
    float[] neu1 = new float[vecSize];
    float[] neu1e = new float[vecSize];
    int[] sentence = new int[MAX_SENTENCE_LEN];

    //traverse all supplied string columns
    for (Chunk chk: cs) if (chk instanceof CStrChunk) {
      while ((sentLen = getSentence(sentence, (CStrChunk) chk)) > 0) {
        for (int sentIdx = 0; sentIdx < sentLen; sentIdx++) {
          if (wrdCnt % 10000 == 0) updateAlpha(wrdCnt);
          curWord = sentence[sentIdx];
          wrdCnt++;
          if (_wordModel == WordModel.CBOW) {
            for (int j = 0; j < vecSize; j++) neu1[j] = 0;
            for (int j = 0; j < vecSize; j++) neu1e[j] = 0;
            bagSize = 0;
          }

          // for each item in the window (except curWord), update neu1 vals
          winSizeMod = cheapRandInt(winSize);
          for (int winIdx = winSizeMod; winIdx < winSize * 2 + 1 - winSizeMod; winIdx++) {
            if (winIdx != winSize) { // skips curWord in sentence
              winWordSentIdx = sentIdx - winSize + winIdx;
              if (winWordSentIdx < 0 || winWordSentIdx >= sentLen) continue;
              winWord = sentence[winWordSentIdx];

              if (_wordModel == WordModel.SkipGram)
                skipGram(curWord, winWord, neu1e);
              else { // CBOW
                for (int j = 0; j < vecSize; j++) neu1[j] += _syn0[j + winWord * vecSize];
                bagSize++;
              }
            }
          } // end for each item in the window
          if (_wordModel == WordModel.CBOW && bagSize > 0)
            CBOW(curWord, sentence, sentIdx, sentLen, winSizeMod, bagSize, neu1, neu1e);
        } // for each item in the sentence
      } // while more sentences
    }
    _output.addLocallyProcessed(wrdCnt);
  }

  @Override public void reduce (WordVectorTrainer other) {
    if (other._output.getLocallyProcessed() > 0 //other task was active (its syn0 should be used for averaging)
            && other._output != _output) //other task worked on a different syn0
    {
      // avoid adding remote model info to unprocessed local data
      // (can happen if master node has no chunks)
      if (_output.getLocallyProcessed() == 0) {
        _output = other._output;
        _chunkNodeCount = other._chunkNodeCount;
      } else {
        _output.add(other._output);
        _chunkNodeCount += other._chunkNodeCount;
      }
    }
  }

  @Override
  protected void closeLocal() {
    _vocab = null;
  }

  static long _lastWarn, _warnCount;
  @Override protected void postGlobal(){
    if (H2O.CLOUD.size() > 1) {
      long now = System.currentTimeMillis();
      if (_chunkNodeCount < H2O.CLOUD.size() && (now - _lastWarn > 5000) && _warnCount < 3) {
        Log.warn(H2O.CLOUD.size() - _chunkNodeCount + " node(s) (out of " + H2O.CLOUD.size()
                + ") are not contributing to model updates. Consider setting replicate_training_data to true or using a larger training dataset (or fewer H2O nodes).");
        _lastWarn = now;
        _warnCount++;
      }
    }
    _output.div(_chunkNodeCount);
    _output.addGloballyProcessed(_output.getLocallyProcessed());
    _output.setLocallyProcessed(0);

    assert(_input == null);
  }

  private void skipGram(int curWord, int winWord, float[] neu1e) {
    final int vecSize = _wordVecSize;
    final int l1 = winWord * vecSize;
    for (int i = 0; i < vecSize; i++) neu1e[i] = 0;

    if (_normModel == NormModel.NegSampling)
      negSamplingSG(curWord, l1, neu1e);
    else // HSM
      hierarchicalSoftmaxSG(curWord, l1, neu1e);

    // Learned weights input -> hidden
    for (int i = 0; i < vecSize; i++) _syn0[i + l1] += neu1e[i];
  }

  private void CBOW(int curWord, int[] sentence, int sentIdx, int sentLen, int winSizeMod, int bagSize, float[] neu1, float[] neu1e) {
    int winWordSentIdx, winWord;
    final int vecSize = _wordVecSize, winSize = _windowSize;
    final int curWinSize = _windowSize * 2 + 1 - winSize;

    for (int i = 0; i < vecSize; i++) neu1[i] /= bagSize;
    if (_normModel == NormModel.NegSampling)
      negSamplingCBOW(curWord, neu1, neu1e);
    else // HSM
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

  private void negSamplingCBOW(final int curWord, final float[] neu1, final float[] neu1e) {
    final int vecSize = _wordVecSize, negExCnt = _negExCnt, uTblSize = _unigramTable.length;
    final float alpha = _curLearningRate;
    float gradient, f=0;
    int targetWord, l2;

    //handle current word
    l2 = curWord * vecSize;
    for (int i = 0; i < vecSize; i++) f += neu1[i] * _syn1[i + l2];

    if (f > MAX_EXP) gradient = 0;
    else if (f < -MAX_EXP) gradient = alpha;
    else gradient = (1 - _expTable[(int)((f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2))]) * alpha;

    for (int i = 0; i < vecSize; i++) neu1e[i] += gradient * _syn1[i + l2];
    for (int i = 0; i < vecSize; i++) _syn1[i + l2] += gradient * neu1[i];

    //pick a negative samples from unigram table
    for (int i = 1; i < negExCnt + 1; i++) {
      f=0;
      targetWord = _unigramTable[cheapRandInt(uTblSize)];
      if (targetWord == curWord) continue;
      l2 = targetWord * vecSize;

      for (int j = 0; j < vecSize; j++) f += neu1[j] * _syn1[j + l2];

      if (f > MAX_EXP) gradient = -alpha;
      else if (f < -MAX_EXP) gradient = 0;
      else gradient =  (-_expTable[(int)((f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2))]) * alpha;

      for (int j = 0; j < vecSize; j++)  neu1e[j] += gradient * _syn1[j + l2];
      for (int j = 0; j < vecSize; j++)  _syn1[j + l2] += gradient * neu1[j];
    }
  }

  private void negSamplingSG(int curWord, int l1, float[] neu1e) {
    final int vecSize = _wordVecSize, negExCnt = _negExCnt, uTblSize = _unigramTable.length;
    final float alpha = _curLearningRate;
    float gradient, f=0;
    int targetWord, l2;

    //handle current word
    l2 = curWord * vecSize;
    for (int i = 0; i < vecSize; i++) f += _syn0[i + l1] * _syn1[i + l2];
    if (f > MAX_EXP) gradient = 0;
    else if (f < -MAX_EXP) gradient = alpha;
    else gradient = (1 - _expTable[(int)((f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2))]) * alpha;

    for (int i = 0; i < vecSize; i++) neu1e[i] += gradient * _syn1[i + l2];
    for (int i = 0; i < vecSize; i++) _syn1[i + l2] += gradient * _syn0[i + l1];

    //pick a negative samples from unigram table
    for (int i = 1; i < negExCnt + 1; i++) {
      f=0;
      targetWord = _unigramTable[cheapRandInt(uTblSize)];
      if (targetWord == curWord) continue;
      l2 = targetWord * vecSize;

      for (int j = 0; j < vecSize; j++) f += _syn0[j + l1] * _syn1[j + l2];
      if (f > MAX_EXP) gradient = -alpha;
      else if (f < -MAX_EXP) gradient = 0;
      else gradient = ( -_expTable[(int)((f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2))]) * alpha;

      for (int j = 0; j < vecSize; j++) neu1e[j] += gradient * _syn1[j + l2];
      for (int j = 0; j < vecSize; j++) _syn1[j + l2] += gradient * _syn0[j + l1];
    }
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

  private void hierarchicalSoftmaxCBOW(final int targetWord, float[] neu1, float[] neu1e) {
    final int vecSize = _wordVecSize, tWrdCodeLen = _HBWTCode[targetWord].length;
    final float alpha = _curLearningRate;
    float gradient, f=0;
    int l2;

    for (int i = 0; i < tWrdCodeLen; i++, f=0) {
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
  private void hierarchicalSoftmaxSG(final int targetWord, final int l1, float[] neu1e) {
    final int vecSize = _wordVecSize, tWrdCodeLen = _HBWTCode[targetWord].length;
    final float alpha = _curLearningRate;
    float gradient, f=0;
    int l2;

    for (int i = 0; i < tWrdCodeLen; i++, f=0) {
      l2 = _HBWTPoint[targetWord][i] * vecSize;

      // Propagate hidden -> output (calc sigmoid)
      for (int j = 0; j < vecSize; j++) f += _syn0[j + l1] * _syn1[j + l2];

      if (f <= -MAX_EXP) continue;
      else if (f >= MAX_EXP) continue;
      else f = _expTable[(int) ((f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2))];

      gradient = (1 - _HBWTCode[targetWord][i] - f) * alpha;
      // Propagate errors output -> hidden
      for (int j = 0; j < vecSize; j++) neu1e[j] += gradient * _syn1[j + l2];
      // Learn weights hidden -> output
      for (int j = 0; j < vecSize; j++) _syn1[j + l2] += gradient * _syn0[j + l1];
    }
  }
}
