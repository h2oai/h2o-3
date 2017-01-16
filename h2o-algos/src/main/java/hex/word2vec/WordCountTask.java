package hex.word2vec;

import water.AutoBuffer;
import water.MRTask;
import water.fvec.Chunk;
import water.parser.BufferedString;
import water.util.IcedHashMap;
import water.util.IcedLong;

import java.util.HashMap;

/**
 * Reduce a string column of a given Vec to a set of unique words
 * and their frequency counts
 *
 * Currently the array is consolidated on the calling node.  Given
 * the limited vocabulary size of most languages, the resulting
 * array is presumed to easily fit in memory.
 */
public class WordCountTask extends MRTask<WordCountTask> {

  // OUT
  IcedHashMap<BufferedString, IcedLong> _counts;

  WordCountTask() {}

  @Override
  public void map(Chunk cs) {
    _counts = new IcedHashMap<>();
    for (int i = 0; i < cs._len; i++) {
      if (cs.isNA(i)) continue;
      BufferedString str = cs.atStr(new BufferedString(), i);
      IcedLong count = _counts.get(str);
      if (count != null)
        count._val++;
      else
        _counts.put(str, new IcedLong(1));
    }
  }

  @Override
  public void reduce(WordCountTask other) {
    assert _counts != null;
    assert other._counts != null;
    for (BufferedString str : other._counts.keySet()) {
      IcedLong myCount = _counts.get(str);
      if (myCount == null)
        _counts.put(str, other._counts.get(str));
      else
        myCount._val += other._counts.get(str)._val;
    }
  }

  public final AutoBuffer write_impl(AutoBuffer ab) {
    if( _counts != null )
      for (BufferedString key : _counts.keySet())
        ab.put2((char)key.length()).putA1(key.getBuffer(), key.getOffset(), key.getOffset() + key.length())
                .put8(_counts.get(key)._val);
    return ab.put2((char)65535); // End of map marker
  }

  public final WordCountTask read_impl(AutoBuffer ab) {
    _counts = new IcedHashMap<>();
    int len;
    while ((len = ab.get2()) != 65535) { // Read until end-of-map marker
      byte[] bs = ab.getA1(len);
      long cnt = ab.get8();
      _counts.put(new BufferedString(new String(bs)), new IcedLong(cnt));
    }
    return this;
  }

}

