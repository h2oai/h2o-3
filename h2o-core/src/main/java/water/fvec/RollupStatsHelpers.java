package water.fvec;

/**
 * DO NOT CHANGE ANY CODE
 */
public class RollupStatsHelpers {
  private final RollupStats _rs;

  RollupStatsHelpers(RollupStats rs) {
    _rs = rs;
  }

  /**
   * MASTER TEMPLATE - All methods below are COPY & PASTED from this template, and some optimizations are performed based on the chunk types
   *
   * @param c
   * @param start
   * @param checksum
   * @return
   */
  public long numericChunkRollup(Chunk c, long start, long checksum) {
    long pinfs=0, ninfs=0, naCnt=0, nzCnt=0;
    // pull (some) members into local variables for speed
    boolean isInt = _rs._isInt;
    boolean hasNA = c.hasNA();
    boolean hasFloat = c.hasFloat();
    double dmin = _rs._mins[_rs._mins.length-1];
    double dmax = _rs._maxs[_rs._maxs.length-1];

    assert(_rs._pinfs == 0); assert(_rs._ninfs == 0); assert(_rs._naCnt == 0); assert(_rs._nzCnt == 0);
    assert(dmin == Double.MAX_VALUE); assert(dmax == -Double.MAX_VALUE);

    long rows = 0; //count of non-NA rows, might be >0 for sparse chunks (all 0s are already processed outside)
    double mean = 0; //mean of non-NA rows, will be 0 for all 0s of sparse chunks
    double M2 = 0; //variance of non-NA rows, will be 0 for all 0s of sparse chunks

    // loop over all values for dense chunks, but only the non-zeros for sparse chunks
    for (int i = c.nextNZ(-1); i < c._len; i = c.nextNZ(i)) {
      if (hasNA && c.isNA(i)) naCnt++;
      else {
        double x = c.atd(i);
        long l = hasFloat ? Double.doubleToRawLongBits(x) : c.at8(i);
        if (l != 0) // ignore 0s in checksum to be consistent with sparse chunks
          checksum ^= (17 * (start + i)) ^ 23 * l;
        if (x == Double.POSITIVE_INFINITY) pinfs++;
        else if (x == Double.NEGATIVE_INFINITY) ninfs++;
        else {
          if (x != 0) nzCnt++;
          if (x < dmin) dmin = _rs.min(x);
          if (x > dmax) dmax = _rs.max(x);
          if (isInt) isInt = (long)x == x;
          rows++;
          double delta = x - mean;
          mean += delta / rows;
          M2 += delta * (x - mean);
        }
      }
    }

    // write back local variables into members
    _rs._pinfs = pinfs;
    _rs._ninfs = ninfs;
    _rs._naCnt = naCnt;
    _rs._nzCnt = nzCnt;
    _rs._rows += rows; // add to pre-filled value for sparse chunks
    _rs._isInt = isInt;
    _rs._mean = mean;
    _rs._sigma = M2;
    return checksum;
  }
}

