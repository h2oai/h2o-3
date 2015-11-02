package hex.word2vec;


import water.H2O;
import water.Key;
import water.MRTask;
import water.Futures;
import water.DKV;
import water.AutoBuffer;
import water.fvec.Vec;
import water.fvec.AppendableVec;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.fvec.CStrChunk;
import water.fvec.Frame;
import water.nbhm.NonBlockingHashMap;
import water.parser.BufferedString;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;


/**
 * Reduce all string columns frame to a set of unique words
 * and their frequency counts
 * <p>
 *   This task operates on all string columns in any frame
 *   handed to it via doAll(). It creates its' results in
 *   its' own frame. If initialized with a minimum frequency,
 *   the returned array will only contain words with counts
 *   greater than or equal to the minimum. </p>
 * <p>
 * Currently the array is consolidated on the calling node.  Given
 * the limited vocabulary size of most languages, the resulting
 * array is presumed to easily fit in memory.  Once this presumption
 * is shown to be incorrect, then this needs to be completely
 * rewritten to be distributed.</p>
 */

public class WordCountTask extends MRTask<WordCountTask> {
  private static NonBlockingHashMap<BufferedStringCount, BufferedStringCount> VOCABHM;
  private NonBlockingHashMap<BufferedStringCount, BufferedStringCount> _vocabHM;
  private transient BufferedStringCount _vocabArray[];
  private final int _minFreq;
  Key _wordCountKey = null;

  public WordCountTask() { _minFreq = 0; }

  public WordCountTask(int minFreq) { _minFreq = minFreq; }

  @Override
  protected void setupLocal()
  {
    VOCABHM = new NonBlockingHashMap();
  }

  /**
   * Iterates over all chunks containing strings, and
   * adds unique instances of those strings to a node
   * local hashmap.  Any pre-existing instance has
   * its count increased instead.
   */
  @Override
  public void map(Chunk cs[]) {
    _vocabHM = VOCABHM;

    for (Chunk chk : cs) if (chk instanceof CStrChunk) {
      BufferedStringCount tmp = new BufferedStringCount();
      for (int row = 0; row < chk._len; row++) {
        chk.atStr(tmp, row);
        BufferedStringCount tmp2 = VOCABHM.get(tmp);
        if (tmp2 == null) {
          VOCABHM.put(tmp, tmp);
          tmp = new BufferedStringCount();
        } else tmp2.inc();
      }
    } // silently ignores other column types
  }

  /**
   *  Local reduces should all see same HM.
   *  Merges between nodes is handled in
   *  {@link #read_impl(water.AutoBuffer)} method.
   */
  @Override
  public void reduce(WordCountTask that) {
    if (_vocabHM != that._vocabHM) throw H2O.unimpl();
  }

  /**
   * Automagically called as a node sends its results
   * to be reduced by another node. This serializes the
   * current node's hashmap for merging.
   */
  @Override
  public AutoBuffer write_impl(AutoBuffer ab) {
    if (_vocabHM == null) return ab.put1(1); // killed

    int strLen = 0;
    for (BufferedStringCount val : VOCABHM.values())
      strLen += val.length();
    ab.put1(0); // not killed
    ab.put4(strLen);  //length of string buffer
    for (BufferedStringCount val : VOCABHM.values())
      ab.put2((char) val.length()).putA1(val.getBuffer(), val.getOffset(), val.getOffset() + val.length()).put8(val._cnt);
    return ab.put2((char) 65535); // End of map marker
  }

  /**
   * Automagically called as a node receives results
   * from another node to be reduced.  This method
   * actually handles the hash map merging as it
   * reads the values.  If a incoming word doesn't
   * exist in the local hashmap, it adds it, otherwise
   * it sums the counts.
   *
   * This keeps the incoming results in a single string
   * buffer.
   */
  @Override
  public WordCountTask read_impl(AutoBuffer ab) {
    _vocabHM = VOCABHM;
    int len, off = 0;
    if (ab.get1() == 1) return this; // killed

    len = ab.get4();
    byte[] buf = new byte[len];
    while ((len = ab.get2()) != 65535) { // Read until end-of-map marker
      BufferedStringCount bsc1 = new BufferedStringCount();
      System.arraycopy(ab.getA1(len), 0, buf, off, len);
      bsc1.set(buf, off, len, ab.get8());
      off += len;
      BufferedStringCount bsc2 = VOCABHM.putIfAbsent(bsc1, bsc1);
      if (bsc2 != null) bsc2.inc(bsc1._cnt); // Inc count on added word
    }
    return this;
  }

  @Override
  protected void copyOver(WordCountTask lv) {
    _vocabHM = lv._vocabHM;
  }

  /**
   * Once hashmap has been consolidated to single node,
   * filter out infrequent words, then sort array
   * according to frequency (descending), finally
   * put the results into a frame.
   */
  @Override
  public void postGlobal() {
    if (_minFreq > 1) filterMin();
    _vocabArray = _vocabHM.values().toArray(new BufferedStringCount[_vocabHM.size()]);
    Arrays.sort(_vocabArray);
    _vocabHM = null;
    VOCABHM = null;
    buildFrame();
  }

  private void filterMin() {
    for (BufferedStringCount str : _vocabHM.values())
      if (str._cnt < _minFreq)
        _vocabHM.remove(str);
  }

  private void buildFrame() {
    Futures fs = new Futures();
    Vec[] vecs = new Vec[2];
    Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(2);

    //allocate
    AppendableVec wordAV = new AppendableVec((keys[0]), Vec.T_STR);
    AppendableVec  cntAV = new AppendableVec((keys[1]), Vec.T_NUM);
    NewChunk wordNC = new NewChunk(wordAV, 0);
    NewChunk cntNC = new NewChunk(cntAV, 0);

    //fill in values
    for (BufferedStringCount str : _vocabArray) {
      wordNC.addStr(str);
      cntNC.addNum(str._cnt, 0);
    }

    //finalize vectors
    wordNC.close(0, fs);
    cntNC.close(0, fs);
    vecs[0] = wordAV.layout_and_close(fs);
    vecs[1] = cntAV.layout_and_close(fs);
    fs.blockForPending();

    if(_fr != null && _fr._key != null) _wordCountKey = Key.make("wca_"+_fr._key.toString());
    else _wordCountKey = Key.make("wca");
    String[] names = {"Word", "Count"};
    DKV.put(_wordCountKey, new Frame(_wordCountKey, names, vecs));
  }

  /**
   * Small extension to the BufferedString class to add
   * an atomic counter for each word. Further, this
   * class sets the values to sort by frequency count
   * first, and then alphabetically second.  The sort
   * is a descending sort.
   */
  protected static class BufferedStringCount extends BufferedString {
    volatile long _cnt = 1;          // Atomically update
    private static final AtomicLongFieldUpdater<BufferedStringCount> _cntUpdater =
            AtomicLongFieldUpdater.newUpdater(BufferedStringCount.class, "_cnt");

    public void inc(long d) {
      long r = _cnt;
      while (!_cntUpdater.compareAndSet(this, r, r + d)) r = _cnt;
    }

    public void inc() {
      long r = _cnt;
      while (!_cntUpdater.compareAndSet(this, r, r + 1)) r = _cnt;
    }

    public BufferedStringCount set(byte[] buf, int off, int len, long cnt) {
      set(buf, off, len);
      long r = _cnt;
      while (!_cntUpdater.compareAndSet(this, r, cnt)) r = _cnt;
      return this;
    }

    //Put sort in descending order
    @Override
    public int compareTo(BufferedString that) {
      final int BEFORE = -1;
      final int EQUAL = 0;
      final int AFTER = 1;

      if (this == that) return EQUAL;
      if (that instanceof BufferedStringCount) {
        long res = ((BufferedStringCount) that)._cnt - this._cnt;
        if (res > 0) return AFTER;
        else if (res < 0) return BEFORE;
      }
      return super.compareTo(that);
    }
  }
}

