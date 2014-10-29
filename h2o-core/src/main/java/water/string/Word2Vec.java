package water.string;

import water.Key;
import water.DKV;
import water.Futures;
import water.fvec.*;
import water.nbhm.NonBlockingHashMap;
import water.util.Log;
import water.parser.ValueString;

/**
 * Generates a word2vec frame from a set of word2vec
 * parameters and a training frame.  Essentially
 * this calls a map/reduce for each epoch.  The reduce
 * phase has the effect of sharing results across nodes.
 * Once complete, a frame containing the results is built
 * along with an internal hashmap for exploring the results
 * with a synonym finder.
 */
public class Word2Vec {
  private Word2VecParameters _params;
  private String _trFrmName = null;
  Frame _w2vFrame;
  Key _w2vKey;
  private NonBlockingHashMap<ValueString, Integer> _vocabHM;

  public Word2Vec(Word2VecParameters p, Frame trainFrame) {
    long start, stop;
    if (p._valid) {
      _params = p;
      _params._trainFrameSize = getTrainFrameSize(trainFrame);

      for (int i = 0; i < _params._epochs; i++) {
        start = System.currentTimeMillis();
        _params = new WordVectorTrainer(_params).doAll(trainFrame)._output;
        stop = System.currentTimeMillis();
        _params._curLearningRate = getLearningRate(_params);
        Log.info("Epoch "+i+" "+(float)(stop-start)/1000+"s");
      }
      _trFrmName = trainFrame._key.toString();
      _w2vKey = buildFrame();
      buildVocabHashMap();

    } else Log.err("Attempting to initialize with invalid parameters.  Word2vec not run. See log for parameter errors.");
  }

  /**
   * Loads w2v results into an H2O frame.
   * @return {@link Key} to results frame
   */
  private Key buildFrame() {
    Futures fs = new Futures();
    String[] colNames = new String[_params._vecSize];
    Vec[] vecs = new Vec[_params._vecSize];
    Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(vecs.length);

    //allocate
    NewChunk cs[] = new NewChunk[vecs.length];
    AppendableVec avs[] = new AppendableVec[vecs.length];
    for (int i = 0; i < vecs.length; i++) {
      avs[i] = new AppendableVec(keys[i]);
      cs[i] = new NewChunk(avs[i], 0);
    }
    //fill in vector values
    for( int i = 0; i < _params._vocabSize; i++ ) {
      for (int j=0;  j < _params._vecSize; j++) {
        cs[j].addNum(_params._syn0[i * _params._vecSize + j]);
      }
    }

    //finalize vectors
    for (int i = 0; i < vecs.length; i++) {
      colNames[i] = new String("V"+i);
      cs[i].close(0, fs);
      vecs[i] = avs[i].close(fs);
    }

    fs.blockForPending();
    _w2vFrame = new Frame(_w2vKey = Key.make("w2v_"+_trFrmName));
    _w2vFrame.add("Word",((Frame)_params._vocabKey.get()).vec(0));
    _w2vFrame.add(colNames, vecs);
    DKV.put(_w2vKey, _w2vFrame);
    return _w2vKey;
  }

  /**
   * Hashmap for quick lookup of a word's row number.
   */
  private void buildVocabHashMap() {
    Vec word = _w2vFrame.vec(0);
    _vocabHM = new NonBlockingHashMap<>((int)_w2vFrame.numRows());
    for(int i=0; i < _w2vFrame.numRows(); i++) _vocabHM.put(word.atStr(new ValueString(),i),i);
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

  /**
   * Calculates a new global learning rate for the next round
   * of map/reduce calls.
   *
   * @param p - parameter object being passed to and from the M/R process
   * @return learningRate - alpha - coefficient that controls the amount that
   * newly learned information effects current learned information.
   */
  private float getLearningRate(Word2VecParameters p) {
    float learningRate = p._initLearningRate * (1 - p.getTotalProcessed() / (float) (p._epochs * p._trainFrameSize + 1));
    if (learningRate < p._initLearningRate * 0.0001F) learningRate = p._initLearningRate * 0.0001F;
    return learningRate;
  }

  /**
   * Takes an input string can return the word vector for that word.
   *
   * @param target - String of desired word
   * @return float array containing the word vector values or null if
   *  the word isn't present in the vocabulary.
   */
  public float[] transform(String target) {
    float[] vec = new float[_params._vecSize];
    ValueString tmp = new ValueString(target);
    if (!_vocabHM.containsKey(tmp)) {
      Log.warn("Target word "+target+" isn't in vocabulary.");
      return null;
    }
    int row = _vocabHM.get(tmp);
    for(int i=0; i < _params._vecSize; i++) vec[i] = (float) _w2vFrame.vec(i+1).at(row);
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
  public void findSynonyms(String target, int cnt) {
    if (cnt > 0) {
      ValueString tmp = new ValueString(target);
      if (!_vocabHM.containsKey(tmp)) {
        Log.warn("Target word "+target+" isn't in vocabulary.");
        return;
      }
      float[] vector = transform(target);
      findSynonyms(vector, cnt);
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
    int[] matches = new int[cnt];
    float [] scores = new float[cnt];
    float[] curVec = new float[_params._vecSize];

    if (tarVec.length != _params._vecSize) {
      Log.warn("Target vector length differs from the vocab's vector length.");
      return;
    }

    for (int i=0; i < _params._vocabSize; i++) {
      for(int j=0; j < _params._vecSize; j++) curVec[j] = (float) _w2vFrame.vec(j+1).at(i);
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
    for (int i=0; i < cnt; i++) System.out.println(_w2vFrame.vec(0).atStr(new ValueString(), matches[i]) + " " + scores[i]);
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

}
