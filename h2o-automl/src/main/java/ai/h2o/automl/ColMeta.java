package ai.h2o.automl;


import ai.h2o.automl.guessers.ColNameScanner;
import water.fvec.Vec;

import java.lang.reflect.Array;

/** Column Meta Data
 *
 * Holds usual rollup stats and additional interesting bits of information.
 */
public class ColMeta {

  public final Vec _v;  // the column
  public final byte _nameType;  // guessed at by ColNameScanner
  public final String _colname;
  public final int _idx;

  // https://0xdata.atlassian.net/browse/STEAM-41 --column metadata to gather
  public long _numUniq;
  public double _numUniqPerChunk;

  public long _timeToHisto;
  public long _timeToMRTask;
  public double _kurtosis;
  public double _skew;
  public double _median;

  // is this an ID?
  //   - all unique
  //   - increasing ints
  public boolean _isID;

  // is it a date column?
  //   - has date info (possibly a month column since values are all 1-12, or 0-11)
  //   - possibly next to other date columns?
  public boolean _isDate;

  public boolean _ignored; // should this column be ignored outright

  public SpecialNA _specialNAs; // found special NAs like 9999 or -1 or 0

  public ColMeta(Vec v, String colname, int idx) {
    _v=v;
    _colname=colname;
    _nameType=ColNameScanner.scan(_colname);
    _idx=idx;
  }


  // stupid wrapper class for possibly special types of NAs; things like 999999 or -1 or 0
  // https://0xdata.atlassian.net/browse/STEAM-76
  static class SpecialNA<T> {
    T[] _nas;
    SpecialNA(Class<T> c, int len) {
      @SuppressWarnings("unchecked")
      final T[] nas = (T[]) Array.newInstance(c, len);
      _nas=nas;
    }
  }
}
