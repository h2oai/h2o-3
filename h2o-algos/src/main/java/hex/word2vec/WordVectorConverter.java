package hex.word2vec;

import water.Job;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.parser.BufferedString;
import water.util.ArrayUtils;

public class WordVectorConverter extends MRTask<WordVectorConverter> {

  // Job
  private final Job<Word2VecModel> _job;

  private final int _wordVecSize;
  private final int _vocabWordCount;

  float[] _syn0;
  BufferedString[] _words;

  public WordVectorConverter(Job<Word2VecModel> job, int wordVecSize, int vocabWordCount) {
    super(null);
    _job = job;
    _wordVecSize = wordVecSize;
    _vocabWordCount = vocabWordCount;
  }

  @Override
  protected void setupLocal() {
    _syn0 = MemoryManager.malloc4f(_wordVecSize * _vocabWordCount);
    _words = new BufferedString[_vocabWordCount];
  }

  @Override
  public void map(Chunk[] cs) {
    int wordPos = (int) cs[0].start();
    int pos = _wordVecSize * wordPos;
    for (int i = 0; i < cs[0]._len; i++) {
      _words[wordPos++] = cs[0].atStr(new BufferedString(), i);
      for (int j = 1; j < cs.length; j++)
        _syn0[pos++] = (float) cs[j].atd(i);
    }
    _job.update(1);
  }

  @Override
  public void reduce(WordVectorConverter other) {
    if (_syn0 != other._syn0) {
      ArrayUtils.add(_syn0, other._syn0);
      for (int i = 0; i < _vocabWordCount; i++) {
        if (other._words[i] != null)
          _words[i] = other._words[i];
      }
    }
  }

}
