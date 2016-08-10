package water.fvec;

import water.*;

import java.util.Arrays;

/**
 * Vec representation of file stored on HDFS.
 */
public final class HDFSFileVec extends FileVec {

  public final String[] _files;
  public final long[] _offsets;

  private HDFSFileVec(Key key, String[] files, long[] offsets, long size) {
    super(key, size, Value.HDFS);
    _files = files;
    _offsets = offsets;
  }

  public int findPartIdx(long skip) {
    int part = Arrays.binarySearch(_offsets, skip);
    part = (part < 0) ? -part-2 : part;
    assert _offsets[part] <= skip;
    return part;
  }

  public long getPartLen(int part) {
    return _offsets[part + 1] - _offsets[part];
  }

  public static Key make(String path, long size) {
    Futures fs = new Futures();
    Key key = make(path, new String[]{path}, new long[]{size}, fs);
    fs.blockForPending();
    return key;
  }

  public static Key make(String path, String[] files, long[] sizes, Futures fs) {
    if (files.length != sizes.length) {
      throw new IllegalArgumentException("Inconsistent parameters, params <files> and <sizes> must have equal length.");
    }
    Key k = Key.make(path);
    Key k2 = Vec.newKey(k);
    new Frame(k).delete_and_lock();
    long[] offsets = cumSum(sizes);
    // Insert the top-level FileVec key into the store
    Vec v = new HDFSFileVec(k2, files, offsets, offsets[sizes.length]);
    DKV.put(k2, v, fs);
    Frame fr = new Frame(k,new String[]{path},new Vec[]{v});
    fr.update();
    fr.unlock();
    return k;
  }

  private static long[] cumSum(long[] input) {
    long[] sums = new long[input.length + 1];
    sums[0] = 0L;
    for (int i = 0; i < input.length; i++) {
      sums[i + 1] = sums[i] + input[i];
    }
    return sums;
  }

}
