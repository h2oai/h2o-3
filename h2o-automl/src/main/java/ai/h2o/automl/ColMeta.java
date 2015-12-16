package ai.h2o.automl;


import ai.h2o.automl.guessers.ColNameScanner;
import ai.h2o.automl.guessers.ProblemTypeGuesser;
import water.Iced;
import water.fvec.Vec;

import java.util.Arrays;

/** Column Meta Data
 *
 * Holds usual rollup stats and additional interesting bits of information.
 */
public class ColMeta extends Iced {
  public final Vec _v;  // the column
  public final byte _nameType;  // guessed at by ColNameScanner
  public final String _name;
  public final int _idx;

  public boolean _ignored;  // should this column be ignored outright
  public boolean _response; // is this a response column?

  // FIRST PASS
  public DynamicHisto _histo;
  public long _timeToMRTask;
  public double _percentNA;  // fraction of NAs in the column
  public SpecialNA _specialNAs; // found special NAs like 9999 or -1 or 0
  private boolean _isClassification;


  // SECOND PASS
  // https://0xdata.atlassian.net/browse/STEAM-41 --column metadata to gather
  public long _numUniq;
  public double _numUniqPerChunk;

  public boolean  _chunksMonotonicallyIncreasing;  //
  public double[] _chkBndries;                     // boundary values for each chunk [c1.min, c1.max, c2.min, c2.max, ...]; only for numeric vecs

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

  public ColMeta(Vec v, String colname, int idx, boolean response) {
    _v = v;
    _name = colname;
    _nameType = ColNameScanner.scan(_name);
    _idx = idx;
    _response = response;
    _specialNAs = new SpecialNA(
            _v.isNumeric()
                    ? (_v.isInt() ? SpecialNA.INT : SpecialNA.DBL)
                    : SpecialNA.STR
    );
    _percentNA = (double)v.naCnt() / (double)v.length();
    _ignored = v.isConst() || v.isString() || v.isBad() || v.isUUID(); // auto ignore from the outset
    if( _response )
      _isClassification = ProblemTypeGuesser.guess(v);
  }

  public boolean isClassification() {
    if( !_response ) throw new UnsupportedOperationException("not a response column");
    return _isClassification;
  }

  // stupid wrapper class for possibly special types of NAs; things like 999999 or -1 or 0
  // https://0xdata.atlassian.net/browse/STEAM-76
  static class SpecialNA extends Iced {
    int[] _ints;
    double[] _dbls;
    String[] _strs;

    byte _type;
    int _idx;
    private static final byte INT=0;
    private static final byte DBL=1;
    private static final byte STR=2;

    SpecialNA(byte type) {
      _type=type;
      switch(type) {
        case INT: _ints=new    int[4]; break;
        case DBL: _dbls=new double[4]; break;
        case STR: _strs=new String[4]; break;
      }
      _idx=0;
    }

    public void add(int val) {
      synchronized (this) {
        if( _idx==_ints.length )
          _ints = Arrays.copyOf(_ints, _idx >> 1);
        _ints[_idx++]=val;
      }
    }
    public void add(double val) {
      synchronized (this) {
        if( _idx==_ints.length )
          _dbls = Arrays.copyOf(_dbls, _idx >> 1);
        _dbls[_idx++]=val;
      }
    }
    public void add(String val) {
      synchronized (this) {
        if( _idx==_ints.length )
          _strs = Arrays.copyOf(_strs, _idx >> 1);
        _strs[_idx++]=val;
      }
    }
  }

  // folds together ideas from ASTHist and DHistogram
  public static class DynamicHisto extends Iced {

  }
}