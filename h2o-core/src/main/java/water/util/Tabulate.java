package water.util;

import hex.Interaction;
import water.DKV;
import water.Job;
import water.Key;
import water.MRTask;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Arrays;

/**
 * Simple Co-Occurrence based tabulation of X vs Y, where X and Y are two Vecs in a given dataset
 * Uses histogram of given resolution in X and Y
 * Handles numerical/categorical data and missing values
 * Supports observation weights
 *
 * Fills up two double[][] arrays:
 * _countData[xbin][ybin] contains the sum of observation weights (or 1) for co-occurrences in bins xbin/ybin
 * _responseData[xbin][2] contains the mean value of Y and the sum of observation weights for a given bin for X
 */
public class Tabulate extends Job<Tabulate> {
  public Frame _dataset;
  public Key _predVec;
  public Key _respVec;
  public String _predictor;
  public String _response;
  public String _weight;
  int _binsPredictor = 20;
  int _binsResponse = 10;

  // result
  double[][] _countData;
  double[][] _responseData;
  public TwoDimTable _countTable;
  public TwoDimTable _responseTable;

  public Tabulate() {
    super(Key.<Tabulate>make(), "Tabulate job");
  }

  private int bins(boolean response) {
    return response ? _binsResponse : _binsPredictor;
  }

  private int res(Vec v) {
    int missing = v.naCnt() > 0 ? 1 : 0;
    if (v.isEnum())
      return v.cardinality() + missing;
    return bins(Arrays.equals(v._key._kb, _respVec._kb)) + missing;
  }

  private int bin(Vec v, double val) {
    int missing = v.naCnt() > 0 ? 1 : 0;
    if (Double.isNaN(val)) {
      assert(missing == 1);
      return 0;
    }

    int b;
    int bins = bins(Arrays.equals(v._key._kb, _respVec._kb));
    if (v.isEnum()) {
      assert((int)val == val);
      b = (int) val;
    } else {
      double d = (v.max() - v.min()) / bins;
      b = (int) ((val - v.min()) / d);
      assert(b>=0 && b<= bins);
      b = Math.min(b, bins -1);//avoid AIOOBE at upper bound
    }
    return b+missing;
  }

  private String labelForBin(Vec v, int b) {
    int missing = v.naCnt() > 0 ? 1 : 0;
    if (missing == 1 && b==0) return "missing(NA)";
    if (missing == 1) b--;
    if (v.isEnum())
      return v.domain()[b];
    int bins = bins(Arrays.equals(v._key._kb, _respVec._kb));
    if (v.isInt() && (v.max() - v.min() + 1) <= bins)
      return Integer.toString((int)(v.min() + b));
    double d = (v.max() - v.min())/ bins;
    return String.format("%5f", v.min() + (b + 0.5) * d);
  }

  public Tabulate execImpl() {
    if (_dataset == null)
      error("_dataset", "Dataset not found");
    if (_binsPredictor < 1)
      error("_binsPredictor", "Number of bins for predictor must be >= 1");
    if (_binsResponse < 1)
      error("_binsResponse", "Number of bins for response must be >= 1");
    Vec x = _dataset.vec(_predictor);
    if (x == null)
      error("_predictor", "Predictor column " + _predictor + " not found");
    else if (x.cardinality() > _binsPredictor) {
      Interaction in = new Interaction();
      in._source_frame = _dataset._key;
      in._factor_columns = new String[]{_predictor};
      in._max_factors = _binsPredictor -1;
      in._dest = Key.make();
      in.execImpl();
      x = ((Frame)DKV.getGet(in._dest)).anyVec();
      in.remove();
    } else if (x.isInt() && (x.max() - x.min() + 1) <= _binsPredictor) {
      x = x.toEnum();
    }
    Vec y = _dataset.vec(_response);
    if (y == null)
      error("_response", "Response column " + _response + " not found");
    else if (y.cardinality() > _binsResponse) {
      Interaction in = new Interaction();
      in._source_frame = _dataset._key;
      in._factor_columns = new String[]{_response};
      in._max_factors = _binsResponse -1;
      in._dest = Key.make();
      in.execImpl();
      y = ((Frame)DKV.getGet(in._dest)).anyVec();
      in.remove();
    } else if (y.isInt() && (y.max() - y.min() + 1) <= _binsResponse) {
      y = y.toEnum();
    }
    if (y!=null && y.cardinality() > 2)
      warn("_response", "Response column has more than two factor levels - mean response depends on lexicographic order of factors!");
    Vec w = _dataset.vec(_weight); //can be null

    if (error_count() > 0){
      Tabulate.this.updateValidationMessages();
      throw new H2OIllegalArgumentException(validationErrors());
    }
    if (x!=null) _predVec = x._key;
    if (y!=null) _respVec = y._key;
    Tabulate sp = w != null ? new CoOccurrence(this).doAll(x, y, w)._sp : new CoOccurrence(this).doAll(x, y)._sp;
    _countTable = sp.tabulationTwoDimTable();
    _responseTable = sp.responseCharTwoDimTable();

    Log.info(_countTable);
    Log.info(_responseTable);
    return sp;
  }

  private static class CoOccurrence extends MRTask<CoOccurrence> {
    final Tabulate _sp;
    CoOccurrence(Tabulate sp) {_sp = sp;}
    @Override
    protected void setupLocal() {
      _sp._countData = new double[_sp.res(_fr.vec(0))][_sp.res(_fr.vec(1))];
      _sp._responseData = new double[_sp.res(_fr.vec(0))][2];
    }

    @Override
    public void map(Chunk x, Chunk y) {
      map(x,y,new C0DChunk(1, x.len()));
    }
    @Override
    public void map(Chunk x, Chunk y, Chunk w) {
      for (int r=0; r<x.len(); ++r) {
        int xbin = _sp.bin(x.vec(), x.atd(r));
        int ybin = _sp.bin(y.vec(), y.atd(r));
        double weight = w.atd(r);
        if (Double.isNaN(weight)) continue;
        AtomicUtils.DoubleArray.add(_sp._countData[xbin], ybin, weight); //increment co-occurrence count by w
        if (!y.isNA(r)) {
          AtomicUtils.DoubleArray.add(_sp._responseData[xbin], 0, weight * y.atd(r)); //add to mean response for x
          AtomicUtils.DoubleArray.add(_sp._responseData[xbin], 1, weight); //increment total for x
        }
      }
    }

    @Override
    public void reduce(CoOccurrence mrt) {
      if (_sp._responseData == mrt._sp._responseData) return;
      ArrayUtils.add(_sp._responseData, mrt._sp._responseData);
    }

    @Override
    protected void postGlobal() {
      //compute mean response
      for (int i=0; i<_sp._responseData.length; ++i) {
        _sp._responseData[i][0] /= _sp._responseData[i][1];
      }
    }
  }

  public TwoDimTable tabulationTwoDimTable() {
    if (_responseData == null) return null;
    int predN = _countData.length;
    int respN = _countData[0].length;
    String tableHeader = "Tabulation between '" + _predictor + "' vs '" + _response + "'";
    String[] rowHeaders = new String[predN * respN];
    String[] colHeaders = new String[3]; //predictor response wcount
    String[] colTypes = new String[colHeaders.length];
    String[] colFormats = new String[colHeaders.length];

    colHeaders[0] = _predictor;
    colHeaders[1] = _response;
    Vec pred = DKV.getGet(_predVec);
    Vec resp = DKV.getGet(_respVec);
    colTypes[0] = "string"; colFormats[0] = "%s";
    colTypes[1] = "string"; colFormats[1] = "%s";
    colHeaders[2] = "volume";   colTypes[2] = "double"; colFormats[2] = "%f";
    TwoDimTable table = new TwoDimTable(
            tableHeader, null/*tableDescription*/, rowHeaders, colHeaders,
            colTypes, colFormats, null);

    for (int p=0; p<predN; ++p) {
      String plabel = labelForBin(pred, p);
      for (int r=0; r<respN; ++r) {
        String rlabel = labelForBin(resp, r);
        for (int c=0; c<3; ++c) {
          table.set(r*predN + p, 0, plabel);
          table.set(r*predN + p, 1, rlabel);
          table.set(r*predN + p, 2, _countData[p][r]);
        }
      }
    }
    return table;
  }

  public TwoDimTable responseCharTwoDimTable() {
    if (_responseData == null) return null;
    String tableHeader = "Characteristics between '" + _predictor + "' and '" + _response + "'";
    int predN = _countData.length;
    String[] rowHeaders = new String[predN]; //X
    String[] colHeaders = new String[3];    //Y
    String[] colTypes = new String[colHeaders.length];
    String[] colFormats = new String[colHeaders.length];

    Vec pred = DKV.getGet(_predVec);

    colHeaders[0] = _predictor;
    colTypes[0] = "string"; colFormats[0] = "%s";
    colHeaders[1] = "mean " + _response; colTypes[2] = "double"; colFormats[2] = "%f";
    colHeaders[2] = "volume";            colTypes[1] = "double"; colFormats[1] = "%f";

    TwoDimTable table = new TwoDimTable(
            tableHeader, null/*tableDescription*/, rowHeaders, colHeaders,
            colTypes, colFormats, null);

    for (int p=0; p<predN; ++p) {
      String plabel = labelForBin(pred, p);
      table.set(p, 0, plabel);
      table.set(p, 1, _responseData[p][0]);
      table.set(p, 2, _responseData[p][1]);
    }
    return table;
  }
}