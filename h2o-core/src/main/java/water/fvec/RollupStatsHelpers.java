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
    double sum = 0;
    double ssqr = 0;
    long pinfs = _rs._pinfs;
    long ninfs = _rs._ninfs;
    long naCnt = _rs._naCnt;
    long nzCnt = _rs._nzCnt;
    long rows = _rs._rows;
    boolean isInt = _rs._isInt;
    boolean hasNA = c.hasNA();
    boolean hasFloat = c.hasFloat();
    double dmin = _rs._mins[_rs._mins.length-1];
    double dmax = _rs._maxs[_rs._maxs.length-1];

    // handle the non-zeros
    for (int i = c.nextNZ(-1); i < c._len; i = c.nextNZ(i)) {
      if (hasNA && c.isNA(i)) naCnt++;
      else {
        double d = c.atd(i);
        long l = hasFloat ? Double.doubleToRawLongBits(d) : c.at8(i);
        if (l != 0) // ignore 0s in checksum to be consistent with sparse chunks
          checksum ^= (17 * (start + i)) ^ 23 * l;
        if (d == Double.POSITIVE_INFINITY) pinfs++;
        else if (d == Double.NEGATIVE_INFINITY) ninfs++;
        else {
          if (d != 0) nzCnt++;
          if (d < dmin) dmin = _rs.min(d);
          if (d > dmax) dmax = _rs.max(d);
          sum += d;
          ssqr += d * d;
          rows++;
          if (isInt && ((long) d) != d) isInt = false;
        }
      }
    }

    _rs._pinfs = pinfs;
    _rs._ninfs = ninfs;
    _rs._naCnt = naCnt;
    _rs._nzCnt = nzCnt;
    _rs._rows = rows;
    _rs._isInt = isInt;
    if (Double.isNaN(_rs._mean)) _rs._mean = sum;
    else _rs._mean += sum;

    // Handle all zero rows
    int zeros = c._len - c.sparseLen();
    if (_rs._rows > 0) {
      final double mean = _rs._mean = _rs._mean / _rs._rows;
      ssqr += mean * mean * zeros; //add contribution of sparse 0s to sum of squares
      _rs._sigma += ssqr - _rs._rows * mean * mean; // _sigma := sum((x-mean(x))^2) = sum(x^2) - N*mean(x)^2
      assert(!Double.isNaN(_rs._sigma));
    }
    return checksum;
  }
}

