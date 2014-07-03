package hex;

import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import static water.util.ArrayUtils.shuffleArray;
import water.util.Log;
import static water.util.RandomUtils.getDeterRNG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public abstract class FrameTask<T extends FrameTask<T>> extends MRTask<T>{
  final protected DataInfo _dinfo;
  final Job _job;
  protected float _useFraction = 1.0f;
  protected boolean _shuffle = false;

//  public FrameTask(Job job, DataInfo dinfo) {
//    this(job,dinfo,null);
//  }
  public FrameTask(Job job, DataInfo dinfo, H2OCountedCompleter cmp) {
    super(cmp);
    _job = job;
    _dinfo = dinfo;
  }
//  protected FrameTask(FrameTask ft){
//    _dinfo = ft._dinfo;
//    _job = ft._job;
//    _useFraction = ft._useFraction;
//    _shuffle = ft._shuffle;
//  }
//  public final double [] normMul(){return _dinfo._normMul;}
//  public final double [] normSub(){return _dinfo._normSub;}
//  public final double [] normRespMul(){return _dinfo._normMul;}
//  public final double [] normRespSub(){return _dinfo._normSub;}

  /**
   * Method to process one row of the data for GLM functions.
   * Numeric and categorical values are passed separately, as is response.
   * Categoricals are passed as absolute indexes into the expanded beta vector, 0-levels are skipped
   * (so the number of passed categoricals will not be the same for every row).
   *
   * Categorical expansion/indexing:
   *   Categoricals are placed in the beginning of the beta vector.
   *   Each cat variable with n levels is expanded into n-1 independent binary variables.
   *   Indexes in cats[] will point to the appropriate coefficient in the beta vector, so e.g.
   *   assume we have 2 categorical columns both with values A,B,C, then the following rows will have following indexes:
   *      A,A - ncats = 0, we do not pass any categorical here
   *      A,B - ncats = 1, indexes = [2]
   *      B,B - ncats = 2, indexes = [0,2]
   *      and so on
   *
   * @param gid      - global id of this row, in [0,_adaptedFrame.numRows())
   * @param nums     - numeric values of this row
   * @param ncats    - number of passed (non-zero) categoricals
   * @param cats     - indexes of categoricals into the expanded beta-vector.
   * @param response - numeric value for the response
   */
  protected void processRow(long gid, double [] nums, int ncats, int [] cats, double [] response){throw new RuntimeException("should have been overridden!");}
  protected void processRow(long gid, double [] nums, int ncats, int [] cats, double [] response, NewChunk [] outputs){throw new RuntimeException("should have been overridden!");}


  public static class DataInfo extends Iced {
    public Frame _adaptedFrame;
    public int _responses; // number of responses
    public boolean _standardize;
    public boolean _standardize_response;
    public boolean _useAllFactorLevels;
    public int _nums;
    public int _cats;
    public int [] _catOffsets;
    public double [] _normMul;
    public double [] _normSub;
    public double [] _normRespMul;
    public double [] _normRespSub;
    public int _foldId;
    public int _nfolds;

    public DataInfo deep_clone() {
      AutoBuffer ab = new AutoBuffer();
      this.write(ab);
      ab.flipForReading();
      return (DataInfo) new DataInfo().read(ab);
    }

    private DataInfo() {_catLvls = null;}
//
//    private DataInfo(DataInfo dinfo, int foldId, int nfolds){
//      assert dinfo._catLvls == null:"Should not be called with filtered levels (assuming the selected levels may change with fold id) ";
//      _standardize = dinfo._standardize;
//      _standardize_response = dinfo._standardize_response;
//      _responses = dinfo._responses;
//      _nums = dinfo._nums;
//      _cats = dinfo._cats;
//      _adaptedFrame = dinfo._adaptedFrame;
//      _catOffsets = dinfo._catOffsets;
//      _normMul = dinfo._normMul;
//      _normSub = dinfo._normSub;
//      _normRespMul = dinfo._normRespMul;
//      _normRespSub = dinfo._normRespSub;
//      _foldId = foldId;
//      _nfolds = nfolds;
//      _useAllFactorLevels = dinfo._useAllFactorLevels;
//      _catLvls = null;
//    }
//    public DataInfo(Frame fr, int hasResponses, boolean useAllFactorLvls, double [] normSub, double [] normMul) {
//      this(fr,hasResponses,useAllFactorLvls, normSub,normMul,null,null);
//    }
//    public DataInfo(Frame fr, int hasResponses, boolean useAllFactorLvls, double [] normSub, double [] normMul, double [] normRespSub, double [] normRespMul){
//      this(fr,hasResponses,useAllFactorLvls,normSub != null && normMul != null, normRespSub != null && normRespMul != null);
//      assert (normSub == null) == (normMul == null);
//      assert (normRespSub == null) == (normRespMul == null);
//      if(normSub != null && normMul != null){
//        System.arraycopy(normSub, 0, _normSub, 0, normSub.length);
//        System.arraycopy(normMul, 0, _normMul, 0, normMul.length);
//      }
//      if(normRespSub != null && normRespMul != null){
//        System.arraycopy(normRespSub, 0, _normRespSub, 0, normRespSub.length);
//        System.arraycopy(normRespMul, 0, _normRespMul, 0, normRespMul.length);
//      }
//    }

    final int [][] _catLvls;

    /**
     * Prepare a Frame (with a single response) to be processed by the FrameTask
     * 1) Place response at the end
     * 2) (Optionally) Remove columns with constant values or with greater than 20% NaNs
     * 3) Possibly turn integer categoricals into enums
     *
     * @param source A frame to be expanded and sanity checked
     * @param response (should be part of source)
     * @param toEnum Whether or not to turn categoricals into enums
     * @param dropConstantCols Whether or not to drop constant columns
     * @return Frame to be used by FrameTask
     */
    public static Frame prepareFrame(Frame source, Vec response, int[] ignored_cols, boolean toEnum, boolean dropConstantCols, boolean dropNACols) {
      Frame fr = new Frame(source._names.clone(), source.vecs().clone());
      if (ignored_cols != null) fr.remove(ignored_cols);
      final Vec[] vecs =  fr.vecs();

      // put response to the end (if not already)
      for(int i = 0; i < vecs.length-1; ++i) {
        if(vecs[i] == response){
          final String n = fr._names[i];
          if (toEnum && !vecs[i].isEnum()) fr.add(n, fr.remove(i).toEnum()); //convert int classes to enums
          else fr.add(n, fr.remove(i));
          break;
        }
      }
      // special case for when response was at the end already
      if (toEnum && !response.isEnum() && vecs[vecs.length-1] == response) {
        final String n = fr._names[vecs.length-1];
        fr.add(n, fr.remove(vecs.length-1).toEnum());
      }

      ArrayList<Integer> constantOrNAs = new ArrayList<>();
      {
        ArrayList<Integer> constantCols = new ArrayList<>();
        ArrayList<Integer> NACols = new ArrayList<>();
        for(int i = 0; i < vecs.length-1; ++i) {
          // remove constant cols and cols with too many NAs
          final boolean dropconstant = dropConstantCols && vecs[i].min() == vecs[i].max();
          final boolean droptoomanyNAs = dropNACols && vecs[i].naCnt() > vecs[i].length()*1;
          if(dropconstant) {
            constantCols.add(i);
          } else if (droptoomanyNAs) {
            NACols.add(i);
          }
        }
        constantOrNAs.addAll(constantCols);
        constantOrNAs.addAll(NACols);

        // Report what is dropped
        String msg = "";
        if (constantCols.size() > 0) msg += "Dropping constant column(s): ";
        for (int i : constantCols) msg += fr._names[i] + " ";
        if (NACols.size() > 0) msg += "Dropping column(s) with too many missing values: ";
        for (int i : NACols) msg += fr._names[i] + " (" + String.format("%.2f", vecs[i].naCnt() * 100. / vecs[i].length()) + "%) ";
        for (String s : msg.split("\n")) Log.info(s);
      }
      if(!constantOrNAs.isEmpty()){
        int [] cols = new int[constantOrNAs.size()];
        for(int i = 0; i < cols.length; ++i)
          cols[i] = constantOrNAs.get(i);
        fr.remove(cols);
      }
      return fr;
    }

//    public static Frame prepareFrame(Frame source, int[] ignored_cols, boolean toEnum, boolean dropConstantCols, boolean dropNACols) {
//      Frame fr = new Frame(source._names.clone(), source.vecs().clone());
//      if (ignored_cols != null) fr.remove(ignored_cols);
//      final Vec[] vecs =  fr.vecs();
//
//      ArrayList<Integer> constantOrNAs = new ArrayList<Integer>();
//      {
//        ArrayList<Integer> constantCols = new ArrayList<Integer>();
//        ArrayList<Integer> NACols = new ArrayList<Integer>();
//        for(int i = 0; i < vecs.length; ++i) {
//          // remove constant cols and cols with too many NAs
//          final boolean dropconstant = dropConstantCols && vecs[i].min() == vecs[i].max();
//          final boolean droptoomanyNAs = dropNACols && vecs[i].naCnt() > vecs[i].length()*0.2;
//          if(dropconstant) {
//            constantCols.add(i);
//          } else if (droptoomanyNAs) {
//            NACols.add(i);
//          }
//        }
//        constantOrNAs.addAll(constantCols);
//        constantOrNAs.addAll(NACols);
//
//        // Report what is dropped
//        String msg = "";
//        if (constantCols.size() > 0) msg += "Dropping constant column(s): ";
//        for (int i : constantCols) msg += fr._names[i] + " ";
//        if (NACols.size() > 0) msg += "Dropping column(s) with too many missing values: ";
//        for (int i : NACols) msg += fr._names[i] + " (" + String.format("%.2f", vecs[i].naCnt() * 100. / vecs[i].length()) + "%) ";
//        for (String s : msg.split("\n")) Log.info(s);
//      }
//      if(!constantOrNAs.isEmpty()){
//        int [] cols = new int[constantOrNAs.size()];
//        for(int i = 0; i < cols.length; ++i)
//          cols[i] = constantOrNAs.get(i);
//        fr.remove(cols);
//      }
//      return fr;
//    }

    public static Frame prepareFrame(Frame source, Vec response, int[] ignored_cols, boolean toEnum, boolean dropConstantCols) {
      return prepareFrame(source, response, ignored_cols, toEnum, dropConstantCols, false);
    }

    public DataInfo(Frame fr, int nResponses, boolean useAllFactors, boolean standardize) {
      this(fr, nResponses, useAllFactors, standardize, false);
    }


    //new DataInfo(f,catLvls, _responses, _standardize, _standardize_response);
//    private DataInfo(Frame fr, int [][] catLevels, int responses, boolean standardize, boolean standardizeResponse, int nfolds, int foldId){
//      _adaptedFrame = fr;
//      _catOffsets = MemoryManager.malloc4(catLevels.length+1);
//      int s = 0;
//
//      for(int i = 0; i < catLevels.length; ++i){
//        _catOffsets[i] = s;
//        s += catLevels[i].length;
//      }
//      _catLvls = catLevels;
//      _catOffsets[_catOffsets.length-1] = s;
//      _responses = responses;
//      _cats = catLevels.length;
//      _nums = fr.numCols()-_cats - responses;
//      if((_standardize = standardize) && _nums > 0){
//        _normMul = MemoryManager.malloc8d(_nums);
//        _normSub = MemoryManager.malloc8d(_nums);
//        for(int i = 0; i < _nums; ++i){
//          Vec v = fr.vec(catLevels.length+i);
//          _normMul[i] = (v.sigma() != 0)?1.0/v.sigma():1.0;
//          _normSub[i] = v.mean();
//        }
//      } else {
//        _normMul = null;
//        _normSub = null;
//      }
//      if((_standardize_response = standardizeResponse) && responses > 0){
//        _normRespMul = MemoryManager.malloc8d(responses);
//        _normRespSub = MemoryManager.malloc8d(responses);
//        for(int i = 0; i < responses; ++i){
//          Vec v = fr.vec(fr.numCols()-responses+i);
//          _normRespSub[i] = (v.sigma() != 0)?1.0/v.sigma():1.0;
//          _normRespSub[i] = v.mean();
//        }
//      } else {
//        _normRespMul = null;
//        _normRespSub = null;
//      }
//      _useAllFactorLevels = false;
//      _adaptedFrame.reloadVecs();
//      _nfolds = nfolds;
//      _foldId = foldId;
//    }
    public DataInfo(Frame fr, int nResponses, boolean useAllFactorLevels, boolean standardize, boolean standardize_response){
      _nfolds = _foldId = 0;
      _standardize = standardize;
      _standardize_response = standardize_response;
      _responses = nResponses;
      _useAllFactorLevels = useAllFactorLevels;
      _catLvls = null;
      final Vec [] vecs = fr.vecs();
      final int n = vecs.length-_responses;
      if (n < 1) throw new IllegalArgumentException("Training data must have at least one column.");
      int [] nums = MemoryManager.malloc4(n);
      int [] cats = MemoryManager.malloc4(n);
      int nnums = 0, ncats = 0;
      for(int i = 0; i < n; ++i){
        if(vecs[i].isEnum())
          cats[ncats++] = i;
        else
          nums[nnums++] = i;
      }
      _nums = nnums;
      _cats = ncats;
      // sort the cats in the decreasing order according to their size
      for(int i = 0; i < ncats; ++i)
        for(int j = i+1; j < ncats; ++j)
          if(vecs[cats[i]].domain().length < vecs[cats[j]].domain().length){
            int x = cats[i];
            cats[i] = cats[j];
            cats[j] = x;
          }
      Vec [] vecs2 = vecs.clone();
      String [] names = fr._names.clone();
      _catOffsets = MemoryManager.malloc4(ncats+1);
      int len = _catOffsets[0] = 0;

      for(int i = 0; i < ncats; ++i){
        Vec v = (vecs2[i] = vecs[cats[i]]);
        names[i] = fr._names[cats[i]];
        _catOffsets[i+1] = (len += v.domain().length - (useAllFactorLevels?0:1));
      }
      if(standardize){
        _normSub = MemoryManager.malloc8d(nnums);
        _normMul = MemoryManager.malloc8d(nnums); Arrays.fill(_normMul, 1);
      } else _normSub = _normMul = null;
      for(int i = 0; i < nnums; ++i){
        Vec v = (vecs2[i+ncats] = vecs[nums[i]]);
        names[i+ncats] = fr._names[nums[i]];
        if(standardize){
          _normSub[i] = v.mean();
          _normMul[i] = v.sigma() != 0 ? 1.0/v.sigma() : 1.0;
        }
      }

      if(standardize_response){
        _normRespSub = MemoryManager.malloc8d(_responses);
        _normRespMul = MemoryManager.malloc8d(_responses); Arrays.fill(_normRespMul, 1);
      } else _normRespSub = _normRespMul = null;
      for(int i = 0; i < _responses; ++i){
        Vec v = (vecs2[nnums+ncats+i] = vecs[nnums+ncats+i]);
        if(standardize_response){
          _normRespSub[i] = v.mean();
          _normRespMul[i] = v.sigma() != 0 ? 1.0/v.sigma() : 1.0;
//          Log.info("normalization for response[" + i + ": mul " + _normRespMul[i] + ", sub " + _normRespSub[i]);
        }
      }
      _adaptedFrame = new Frame(names,vecs2);
      _adaptedFrame.reloadVecs();
    }

//    public DataInfo filterExpandedColumns(int [] cols){
//      int i = 0, j = 0, ignoredCnt = 0;
//      //public DataInfo(Frame fr, int hasResponses, boolean useAllFactorLvls, double [] normSub, double [] normMul, double [] normRespSub, double [] normRespMul){
//      int [][] catLvls = new int[_cats][];
//      int [] ignoredCols = MemoryManager.malloc4(_nums + _cats);
//      // first do categoricals...
//      while(i < cols.length && cols[i] < _catOffsets[_catOffsets.length-1]){
//        int [] levels = MemoryManager.malloc4(_catOffsets[j+1] - _catOffsets[j]);
//        int k = 0;
//        while(i < cols.length && cols[i] < _catOffsets[j+1])
//          levels[k++] = cols[i++]-_catOffsets[j];
//        if(k > 0)
//          catLvls[j] = Arrays.copyOf(levels, k);
//        ++j;
//      }
//      for(int k =0; k < catLvls.length; ++k)
//        if(catLvls[k] == null)ignoredCols[ignoredCnt++] = k;
//      if(ignoredCnt > 0){
//        int [][] c = new int[_cats-ignoredCnt][];
//        int y = 0;
//        for(int x = 0; x < catLvls.length; ++x)
//          if(catLvls[x] != null) c[y++] = catLvls[x];
//        assert y == c.length;
//        catLvls = c;
//      }
//      // now numerics
//      int prev = j = 0;
//      for(; i < cols.length; ++i){
//        for(int k = prev; k < (cols[i]-numStart()); ++k ){
//          ignoredCols[ignoredCnt++] = k+_cats;
//          ++j;
//        }
//        prev = ++j;
//      }
//      for(int k = prev; k < _nums; ++k)
//        ignoredCols[ignoredCnt++] = k+_cats;
//      Frame f = new Frame(_adaptedFrame.names().clone(),_adaptedFrame.vecs().clone());
//      if(ignoredCnt > 0) f.remove(Arrays.copyOf(ignoredCols,ignoredCnt));
//      assert catLvls.length < f.numCols():"cats = " + catLvls.length + " numcols = " + f.numCols();
//      return new DataInfo(f,catLvls, _responses, _standardize, _standardize_response, _nfolds, _foldId);
//    }
    public String toString(){
      return "";
    }
//    public DataInfo getFold(int foldId, int nfolds){
//      return new DataInfo(this, foldId, nfolds);
//    }
    public final int fullN(){return _nums + _catOffsets[_cats];}
//    public final int largestCat(){return _cats > 0?_catOffsets[1]:0;}
    public final int numStart(){return _catOffsets[_cats];}
    public final String [] coefNames(){
      int k = 0;
      final int n = fullN();
      String [] res = new String[n];
      final Vec [] vecs = _adaptedFrame.vecs();
      for(int i = 0; i < _cats; ++i)
        for(int j = _useAllFactorLevels?0:1; j < vecs[i].factors().length; ++j)
          res[k++] = _adaptedFrame._names[i] + "." + vecs[i].factors()[j];
      final int nums = n-k;
      System.arraycopy(_adaptedFrame._names, _cats, res, k, nums);
      return res;
    }
  }

  @Override
  public T dfork(Frame fr){
    assert fr == _dinfo._adaptedFrame;
    return super.dfork(fr);
  }

  /**
   * Override this to initialize at the beginning of chunk processing.
   */
  protected void chunkInit(){}
  /**
   * Override this to do post-chunk processing work.
   */
  protected void chunkDone(){}


  /**
   * Extracts the values, applies regularization to numerics, adds appropriate offsets to categoricals,
   * and adapts response according to the CaseMode/CaseValue if set.
   */
  @Override public final void map(Chunk [] chunks, NewChunk [] outputs){
    if(_job != null && _job._key != null && !Job.isRunning(_job._key)) throw new RuntimeException("Job cancelled");
    final int nrows = chunks[0].len();
    final long offset = chunks[0].start();
    chunkInit();
    double [] nums = MemoryManager.malloc8d(_dinfo._nums);
    int    [] cats = MemoryManager.malloc4(_dinfo._cats);
    double [] response = MemoryManager.malloc8d(_dinfo._responses);
    int start = 0;
    int end = nrows;

    boolean contiguous = false;
    Random skip_rng = null; //random generator for skipping rows

    //Example:
    // _useFraction = 0.8 -> 1 repeat with fraction = 0.8
    // _useFraction = 1.0 -> 1 repeat with fraction = 1.0
    // _useFraction = 1.1 -> 2 repeats with fraction = 0.55
    // _useFraction = 2.1 -> 3 repeats with fraction = 0.7
    // _useFraction = 3.0 -> 3 repeats with fraction = 1.0
    final int repeats = (int)Math.ceil(_useFraction);
    final float fraction = _useFraction / repeats;

    if (fraction < 1.0) {
      skip_rng = getDeterRNG(new Random().nextLong());
      if (contiguous) {
        final int howmany = (int)Math.ceil(fraction*nrows);
        if (howmany > 0) {
          start = skip_rng.nextInt(nrows - howmany);
          end = start + howmany;
        }
        assert(start < nrows);
        assert(end <= nrows);
      }
    }

    long[] shuf_map = null;
    if (_shuffle) {
      shuf_map = new long[end-start];
      for (int i=0;i<shuf_map.length;++i)
        shuf_map[i] = start + i;
      shuffleArray(shuf_map, new Random().nextLong());
    }
    for(int rrr = 0; rrr < repeats; ++rrr) {
    OUTER:
      for(int rr = start; rr < end; ++rr){
        final int r = shuf_map != null ? (int)shuf_map[rr-start] : rr;
        if ((_dinfo._nfolds > 0 && (r % _dinfo._nfolds) == _dinfo._foldId)
                || (skip_rng != null && skip_rng.nextFloat() > _useFraction))continue;
        for(Chunk c:chunks)if(c.isNA0(r))continue OUTER; // skip rows with NAs!
        int i = 0, ncats = 0;
        for(; i < _dinfo._cats; ++i){
          int c = (int)chunks[i].at80(r);
          if(_dinfo._catLvls != null){ // some levels are ignored?
            c = Arrays.binarySearch(_dinfo._catLvls[i],c);
            if(c >= 0)
              cats[ncats++] = c + _dinfo._catOffsets[i];
          } else if(_dinfo._useAllFactorLevels)
            cats[ncats++] = c + _dinfo._catOffsets[i];
          else if(c != 0)
            cats[ncats++] = c + _dinfo._catOffsets[i]-1;
        }
        final int n = chunks.length-_dinfo._responses;
        for(;i < n;++i){
          double d = chunks[i].at0(r);
          if(_dinfo._normMul != null) d = (d - _dinfo._normSub[i-_dinfo._cats])*_dinfo._normMul[i-_dinfo._cats];
          nums[i-_dinfo._cats] = d;
        }
        for(i = 0; i < _dinfo._responses; ++i) {
          response[i] = chunks[chunks.length-_dinfo._responses + i].at0(r);
          if (_dinfo._normRespMul != null) response[i] = (response[i] - _dinfo._normRespSub[i])*_dinfo._normRespMul[i];
        }
        if (outputs != null && outputs.length > 0)
          processRow(offset + r, nums, ncats, cats, response, outputs);
        else
          processRow(offset + r, nums, ncats, cats, response);
      }
    }
    chunkDone();
  }
}
