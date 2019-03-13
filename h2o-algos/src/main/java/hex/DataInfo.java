package hex;

import water.*;
import water.fvec.*;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by tomasnykodym on 1/29/15.
 *
 * Provides higher level interface for accessing data row-wise.
 *
 * Performs on the fly auto-expansion of categorical variables (to 1 hot encoding) and standardization ( or normalize/demean/descale/none) of predictors and response.
 * Supports sparse data, sparse columns can be transformed to sparse rows on the fly with some (significant) memory overhead,
 * as the data of the whole chunk(s) will be copied.
 *
*/
public class DataInfo extends Keyed<DataInfo> {
  public int [] _activeCols;
  public Frame _adaptedFrame;  // the modified DataInfo frame (columns sorted by largest categorical -> least then all numerical columns)
  public int _responses;   // number of responses
  public int _outpus; // number of outputs

  public Vec setWeights(String name, Vec vec) {
    if(_weights)
      return _adaptedFrame.replace(weightChunkId(),vec);
    _adaptedFrame.insertVec(weightChunkId(),name,vec);
    _weights = true;
    return null;
  }

  public void dropWeights() {
    if(!_weights)return;
    _adaptedFrame.remove(weightChunkId());
    _weights = false;
  }

  public void dropInteractions() { // only called to cleanup the InteractionWrappedVecs!
    if(_interactions!=null) {
      Vec[] vecs = _adaptedFrame.remove(_interactionVecs);
      for(Vec v:vecs)v.remove();
      _interactions = null;
    }
  }

  public int[] activeCols() {
    if(_activeCols != null) return _activeCols;
    int [] res = new int[fullN()+1];
    for(int i = 0; i < res.length; ++i)
      res[i] = i;
    return res;
  }

  public void addResponse(String [] names, Vec[] vecs) {
    _adaptedFrame.add(names,vecs);
    _responses += vecs.length;
  }

  public int[] catNAFill() {return _catNAFill;}
  public int catNAFill(int cid) {return _catNAFill[cid];}

  public void setCatNAFill(int[] catNAFill) {
    _catNAFill = catNAFill;
  }

  public double normSub(int i) {
    return _normSub == null?0:_normSub[i];
  }

  public double normMul(int i) {
    return _normMul == null?1:_normMul[i];
  }

  public enum TransformType {
    NONE, STANDARDIZE, NORMALIZE, DEMEAN, DESCALE;

    public boolean isMeanAdjusted(){
      switch(this){
        case NONE:
        case DESCALE:
        case NORMALIZE:
          return false;
        case STANDARDIZE:
        case DEMEAN:
          return true;
        default:
          throw H2O.unimpl();
      }
    }
    public boolean isSigmaScaled(){
      switch(this){
        case NONE:
        case DEMEAN:
        case NORMALIZE:
          return false;
        case STANDARDIZE:
        case DESCALE:
          return true;
        default:
          throw H2O.unimpl();
      }
    }
  }
  public TransformType _predictor_transform;
  public TransformType _response_transform;
  public boolean _useAllFactorLevels;
  public int _nums;  // "raw" number of numerical columns as they exist in the frame
  public int _cats;  // "raw" number of categorical columns as they exist in the frame
  public int [] _catOffsets;   // offset column indices for the 1-hot expanded values (includes enum-enum interaction)
  public boolean [] _catMissing;  // bucket for missing levels
  private int [] _catNAFill;    // majority class of each categorical col (or last bucket if _catMissing[i] is true)
  public int [] _permutation; // permutation matrix mapping input col indices to adaptedFrame
  public double [] _normMul;  // scale the predictor column by this value
  public double [] _normSub;  // subtract from the predictor this value
  public double [] _normRespMul;  // scale the response column by this value
  public double [] _normRespSub;  // subtract from the response column this value
  public double [] _numMeans;
  public boolean _intercept = true;
  public boolean _offset;
  public boolean _weights;
  public boolean _fold;
  public Model.InteractionPair[] _interactions; // raw set of interactions
  public Model.InteractionSpec _interactionSpec; // formal specification of interactions
  public int _interactionVecs[]; // the interaction columns appearing in _adaptedFrame
  public int[] _numOffsets; // offset column indices used by numerical interactions: total number of numerical columns is given by _numOffsets[_nums] - _numOffsets[0]
  public int responseChunkId(int n){return n + _cats + _nums + (_weights?1:0) + (_offset?1:0) + (_fold?1:0);}
  public int foldChunkId(){return _cats + _nums + (_weights?1:0) + (_offset?1:0);}

  public int offsetChunkId(){return _cats + _nums + (_weights ?1:0);}
  public int weightChunkId(){return _cats + _nums;}
  public int outputChunkId() { return outputChunkId(0);}
  public int outputChunkId(int n) { return n + _cats + _nums + (_weights?1:0) + (_offset?1:0) + (_fold?1:0) + _responses;}
  public void addOutput(String name, Vec v) {_adaptedFrame.add(name,v);}
  public Vec getOutputVec(int i) {return _adaptedFrame.vec(outputChunkId(i));}
  public void setResponse(String name, Vec v){ setResponse(name,v,0);}
  public void setResponse(String name, Vec v, int n){ _adaptedFrame.insertVec(responseChunkId(n),name,v);}

  public final boolean _skipMissing;
  public final boolean _imputeMissing;
  public boolean _valid; // DataInfo over validation data set, can have unseen (unmapped) categorical levels
  public final int [][] _catLvls; // cat lvls post filter (e.g. by strong rules)
  public final int [][] _intLvls; // interaction lvls post filter (e.g. by strong rules)

  private DataInfo() {  _intLvls=null; _catLvls = null; _skipMissing = true; _imputeMissing = false; _valid = false; _offset = false; _weights = false; _fold = false; }
  public String[] _coefNames;
  @Override protected long checksum_impl() {throw H2O.unimpl();} // don't really need checksum

  // Modify the train & valid frames directly; sort the categorical columns
  // up front according to size; compute the mean/sigma for each column for
  // later normalization.
  public DataInfo(Frame train, Frame valid, boolean useAllFactorLevels, TransformType predictor_transform, boolean skipMissing, boolean imputeMissing, boolean missingBucket) {
    this(train, valid, 0, useAllFactorLevels, predictor_transform, TransformType.NONE, skipMissing, imputeMissing, missingBucket, /* weight */ false, /* offset */ false, /* fold */ false, /* intercept */ false);
  }

  public DataInfo(Frame train, Frame valid, int nResponses, boolean useAllFactorLevels, TransformType predictor_transform, TransformType response_transform, boolean skipMissing, boolean imputeMissing, boolean missingBucket, boolean weight, boolean offset, boolean fold) {
    this(train,valid,nResponses,useAllFactorLevels,predictor_transform,response_transform,skipMissing,imputeMissing,missingBucket,weight,offset,fold,null);
  }

  /**
   *
   * The train/valid Frame instances are sorted by categorical (themselves sorted by
   * cardinality greatest to least) with all numerical columns following. The response
   * column(s) are placed at the end.
   *
   *
   * Interactions:
   *  1. Num-Num (Note: N(0,1) * N(0,1) ~ N(0,1) )
   *  2. Num-Enum
   *  3. Enum-Enum
   *
   *  Interactions are produced on the fly and are dense (in all 3 cases). Consumers of
   *  DataInfo should not have to care how these interactions are generated. Any heuristic
   *  using the fullN value should continue functioning the same.
   *
   *  Interactions are specified in two ways:
   *    A. As a list of pairs of column indices.
   *    B. As a list of pairs of column indices with limited enums.
   */
  public DataInfo(Frame train, Frame valid, int nResponses, boolean useAllFactorLevels, TransformType predictor_transform, TransformType response_transform, boolean skipMissing, boolean imputeMissing, boolean missingBucket, boolean weight, boolean offset, boolean fold, Model.InteractionSpec interactions) {
    super(Key.<DataInfo>make());
    assert predictor_transform != null;
    assert response_transform != null;
    _valid = valid != null;
    _offset = offset;
    _weights = weight;
    _fold = fold;
    assert !(skipMissing && imputeMissing) : "skipMissing and imputeMissing cannot both be true";
    _skipMissing = skipMissing;
    _imputeMissing = imputeMissing;
    _predictor_transform = predictor_transform;
    _response_transform = response_transform;
    _responses = nResponses;
    _useAllFactorLevels = useAllFactorLevels;
    _interactionSpec = interactions;
    if (interactions != null) {
      train = interactions.reorderColumns(train);
      valid = interactions.reorderColumns(valid);
      _interactions = interactions.makeInteractionPairs(train);
    }

    // create dummy InteractionWrappedVecs and shove them onto the front
    if( _interactions!=null ) {
      _interactionVecs=new int[_interactions.length];
      Frame inter = Model.makeInteractions(train, false, _interactions, _useAllFactorLevels, _skipMissing,predictor_transform==TransformType.STANDARDIZE);
      train = inter.add(_interactionSpec.removeInteractionOnlyColumns(train));
      if( valid!=null ) {
        inter = Model.makeInteractions(valid, true, _interactions, _useAllFactorLevels, _skipMissing, predictor_transform == TransformType.STANDARDIZE); // FIXME: should be using the training subs/muls!
        valid = inter.add(_interactionSpec.removeInteractionOnlyColumns(valid));
      }
    }

    _permutation = new int[train.numCols()];
    final Vec[] tvecs = train.vecs();

    // Count categorical-vs-numerical
    final int n = tvecs.length-_responses - (offset?1:0) - (weight?1:0) - (fold?1:0);
    int [] nums = MemoryManager.malloc4(n);
    int [] cats = MemoryManager.malloc4(n);
    int nnums = 0, ncats = 0;
    for(int i = 0; i < n; ++i)
      if (tvecs[i].isCategorical())
        cats[ncats++] = i;
      else
        nums[nnums++] = i;

    _nums = nnums;
    _cats = ncats;
    _catLvls = new int[ncats][];

    // sort the cats in the decreasing order according to their size
    for(int i = 0; i < ncats; ++i)
      for(int j = i+1; j < ncats; ++j)
        if( tvecs[cats[i]].domain().length < tvecs[cats[j]].domain().length ) {
          int x = cats[i];
          cats[i] = cats[j];
          cats[j] = x;
        }
    String[] names = new String[train.numCols()];
    Vec[] tvecs2 = new Vec[train.numCols()];

    // Compute the cardinality of each cat
    _catNAFill = new int[ncats];
    _catOffsets = MemoryManager.malloc4(ncats+1);
    _catMissing = new boolean[ncats];
    int len = _catOffsets[0] = 0;
    int interactionIdx=0; // simple index into the _interactionVecs array

    ArrayList<Integer> interactionIds;
    if( _interactions==null ) {
      interactionIds = new ArrayList<>();
      for(int i=0;i<tvecs.length;++i)
        if( tvecs[i] instanceof InteractionWrappedVec ) interactionIds.add(i);
      if( interactionIds.size() > 0 ) {
        _interactionVecs = new int[interactionIds.size()];
        for (int i = 0; i < _interactionVecs.length; ++i)
          _interactionVecs[i] = interactionIds.get(i);
      }
    }
    for(int i = 0; i < ncats; ++i) {
      names[i] = train._names[cats[i]];
      Vec v = (tvecs2[i] = tvecs[cats[i]]);
      _catMissing[i] = missingBucket; //needed for test time
      if( v instanceof InteractionWrappedVec ) {
        _interactionVecs[interactionIdx++]=i;  // i (and not cats[i]) because this is the index in _adaptedFrame
        _catOffsets[i + 1] = (len += v.domain().length + (missingBucket ? 1 : 0));
      }
      else
        _catOffsets[i+1] = (len += v.domain().length - (useAllFactorLevels?0:1) + (missingBucket? 1 : 0)); //missing values turn into a new factor level
      _catNAFill[i] = imputeMissing?imputeCat(train.vec(cats[i]),_useAllFactorLevels):_catMissing[i]?v.domain().length - (_useAllFactorLevels || isInteractionVec(i)?0:1):-100;
      _permutation[i] = cats[i];
    }
    _numOffsets = MemoryManager.malloc4(nnums+1);
    _numOffsets[0]=len;
    boolean isIWV; // is InteractionWrappedVec?
    for(int i = 0; i < nnums; ++i) {
      names[i+ncats] = train._names[nums[i]];
      Vec v = train.vec(nums[i]);
      tvecs2[i+ncats] = v;
      isIWV = v instanceof InteractionWrappedVec;
      if( isIWV ) {
        _interactionVecs[interactionIdx++]=i+ncats;
      }
      _numOffsets[i+1] = (len+= (isIWV ? ((InteractionWrappedVec) v).expandedLength() : 1));
      _permutation[i+ncats] = nums[i];
    }
    _numMeans = new double[numNums()];
    int meanIdx=0;
    for(int i=0;i<nnums;++i) {
      Vec v = train.vec(nums[i]);
      if( v instanceof InteractionWrappedVec ) {
        InteractionWrappedVec iwv = (InteractionWrappedVec)v;
        double[] means = iwv.getMeans();
        int start = iwv._useAllFactorLevels?0:1;
        int length   = iwv.expandedLength();
        System.arraycopy(means,start,_numMeans,meanIdx,length);
        meanIdx+=length;
      }
      else
        _numMeans[meanIdx++]=v.mean();
    }
    for(int i = names.length-nResponses - (weight?1:0) - (offset?1:0) - (fold?1:0); i < names.length; ++i) {
      names[i] = train._names[i];
      tvecs2[i] = train.vec(i);
    }
    _adaptedFrame = new Frame(names,tvecs2);
    train.restructure(names,tvecs2);
    if (valid != null)
      valid.restructure(names,valid.vecs(names));
//    _adaptedFrame = train;

    setPredictorTransform(predictor_transform);
    if(_responses > 0)
      setResponseTransform(response_transform);
    _intLvls = new int[_interactionVecs==null?0:_interactionVecs.length][];
  }

  public DataInfo disableIntercept() {
    _intercept = false;
    return this;
  }

  public DataInfo(Frame train, Frame valid, int nResponses, boolean useAllFactorLevels, TransformType predictor_transform, TransformType response_transform, boolean skipMissing, boolean imputeMissing, boolean missingBucket, boolean weight, boolean offset, boolean fold, boolean intercept) {
    this(train, valid, nResponses, useAllFactorLevels, predictor_transform, response_transform, skipMissing, imputeMissing, missingBucket, weight, offset, fold);
    _intercept = intercept;
  }

  public DataInfo validDinfo(Frame valid) {
    DataInfo res = new DataInfo(_adaptedFrame,null,1,_useAllFactorLevels,TransformType.NONE,TransformType.NONE,_skipMissing,_imputeMissing,!(_skipMissing || _imputeMissing),_weights,_offset,_fold);
    res._interactions = _interactions;
    res._interactionSpec = _interactionSpec;
    if (_interactionSpec != null) {
      valid = Model.makeInteractions(valid, true, _interactions, _useAllFactorLevels, _skipMissing, false).add(valid);
    }
    res._adaptedFrame = new Frame(_adaptedFrame.names(),valid.vecs(_adaptedFrame.names()));
    res._valid = true;
    return res;
  }

  public double[] denormalizeBeta(double [] beta) {
    int N = fullN()+1;
    assert (beta.length % N) == 0:"beta len = " + beta.length + " expected multiple of" + N;
    int nclasses = beta.length/N;
    beta = MemoryManager.arrayCopyOf(beta,beta.length);
    if (_predictor_transform == DataInfo.TransformType.STANDARDIZE) {
      for(int c = 0; c < nclasses; ++c) {
        int off = N*c;
        double norm = 0.0;        // Reverse any normalization on the intercept
        // denormalize only the numeric coefs (categoricals are not normalized)
        final int numoff = numStart();
        for (int i = numoff; i < N-1; i++) {
          double b = beta[off + i] * _normMul[i - numoff];
          norm += b * _normSub[i - numoff]; // Also accumulate the intercept adjustment
          beta[off + i] = b;
        }
        beta[off + N - 1] -= norm;
      }
    }
    return beta;
  }

  private int [] _fullCatOffsets;
  private int [][] _catMap;

  protected int [] fullCatOffsets(){ return _fullCatOffsets == null?_catOffsets:_fullCatOffsets;}
  // private constructor called by filterExpandedColumns
  private DataInfo(DataInfo dinfo,Frame fr, double [] normMul, double [] normSub, int[][] catLevels, int[][] intLvls, int [] catModes, int[] activeCols) {
    _activeCols=activeCols;
    _fullCatOffsets = dinfo._catOffsets;
    if(!dinfo._useAllFactorLevels) {
      _fullCatOffsets = dinfo._catOffsets.clone();
      for (int i = 0; i < _fullCatOffsets.length; ++i)
        _fullCatOffsets[i] += i; // add for the skipped zeros.
    }
    _cats = catLevels.length;
    _catMap = new int[_cats][];
    _offset = dinfo._offset;
    _weights = dinfo._weights;
    _fold = dinfo._fold;
    _valid = false;
    _interactions = null;
    ArrayList<Integer> interactionVecs = new ArrayList<>();
    for(int i=0;i<fr.numCols();++i)
      if( fr.vec(i) instanceof InteractionWrappedVec ) interactionVecs.add(i);

    if( interactionVecs.size() > 0 ) {
      _interactionVecs = new int[interactionVecs.size()];
      for (int i = 0; i < _interactionVecs.length; ++i)
        _interactionVecs[i] = interactionVecs.get(i);
    }
    assert dinfo._predictor_transform != null;
    assert  dinfo._response_transform != null;
    _predictor_transform = dinfo._predictor_transform;
    _response_transform  =  dinfo._response_transform;
    _skipMissing = dinfo._skipMissing;
    _imputeMissing = dinfo._imputeMissing;
    _adaptedFrame = fr;
    _catOffsets = MemoryManager.malloc4(catLevels.length + 1);
    _catMissing = new boolean[catLevels.length];
    Arrays.fill(_catMissing,!(dinfo._imputeMissing || dinfo._skipMissing));
    int s = 0;
    for(int i = 0; i < catLevels.length; ++i){
      if(catLevels[i] != null) {
        _catMap[i] = new int[_adaptedFrame.vec(i).cardinality()];
        Arrays.fill(_catMap[i],-1);
        for (int j = 0; j < catLevels[i].length; j++) {
          _catMap[i][catLevels[i][j]] = j;
        }
      }
      _catOffsets[i] = s;
      s += catLevels[i].length;
    }
    _catOffsets[_catOffsets.length-1] = s;
    _catLvls = catLevels;
    _intLvls = intLvls;
    _responses = dinfo._responses;

    _useAllFactorLevels = true;//dinfo._useAllFactorLevels;
    _normMul = normMul;
    _normSub = normSub;
    _catNAFill = catModes;
  }


  public static int imputeCat(Vec v) {return imputeCat(v,true);}
  public static int imputeCat(Vec v, boolean useAllFactorLevels) {
    if(v.isCategorical()) {
      if (useAllFactorLevels) return v.mode();
      long[] bins = v.bins();
      return ArrayUtils.maxIndex(bins,1);
    }
    return (int)Math.round(v.mean());
  }


  /**
   * Filter the _adaptedFrame so that it contains only the Vecs referenced by the cols
   * parameter.
   *
   * @param cols Array of the expanded column indices to keep.
   * @return A DataInfo with _activeCols specifying the active columns
   */
  public DataInfo filterExpandedColumns(int [] cols){
    assert _activeCols==null;
    assert _predictor_transform != null;
    assert  _response_transform != null;
    if(cols == null)return IcedUtils.deepCopy(this);  // keep all columns
    int hasIcpt = (cols.length > 0 && cols[cols.length-1] == fullN())?1:0;
    int i = 0, j = 0, ignoredCnt = 0;
    //public DataInfo(Frame fr, int hasResponses, boolean useAllFactorLvls, double [] normSub, double [] normMul, double [] normRespSub, double [] normRespMul){
    int [][] catLvls = new int[_cats][];  // categorical levels to keep (used in getCategoricalOffsetId binary search)
    int [][] intLvls = new int[_interactionVecs==null?0:_interactionVecs.length][]; // interactions levels to keep (used in getInteractionOffsetId binary search)
    int [] ignoredCols = MemoryManager.malloc4(_nums + _cats);  // capital 'v' Vec indices to be frame.remove'd
    // first do categoricals...
    if(_catOffsets != null) {
      int coff = _useAllFactorLevels?0:1;
      while (i < cols.length && cols[i] < numStart()) {  // iterate over categorical cols
        int[] levels = MemoryManager.malloc4(_catOffsets[j + 1] - _catOffsets[j]);
        int k = 0;  // keep track of how many levels we have (so we can "trim" the levels array when inserting into catLvls)
        while (i < cols.length && cols[i] < _catOffsets[j + 1])
          levels[k++] = (cols[i++] - _catOffsets[j]) + coff;
        if (k > 0)
          catLvls[j] = Arrays.copyOf(levels, k);
        ++j;
      }
    }
    int [] catModes = _catNAFill;
    for(int k =0; k < catLvls.length; ++k)
      if(catLvls[k] == null)ignoredCols[ignoredCnt++] = k;
    if(ignoredCnt > 0){
      int [][] cs = new int[_cats-ignoredCnt][];
      catModes = new int[_cats-ignoredCnt];
      int y = 0;
      for (int c = 0; c < catLvls.length; ++c)
        if (catLvls[c] != null) {
          catModes[y] = _catNAFill[c];
          cs[y++] = catLvls[c];
        }
      assert y == cs.length;
      catLvls = cs;
    }

    // now do the interaction vecs -- these happen to always sit first in the "nums" section of _adaptedFrame
    // also, these have the exact same filtering logic as the categoricals above
    int prev=j=0; // reset j for _numOffsets
    if( _interactionVecs!=null ) {
      while( i < cols.length && cols[i] < _numOffsets[intLvls.length]) {
        int[] lvls = MemoryManager.malloc4(_numOffsets[j+1] - _numOffsets[j]);
        int k=0; // same as above
        while(i<cols.length && cols[i] < _numOffsets[j+1])
          lvls[k++] = (cols[i++] - _numOffsets[j]); // no useAllFactorLevels offset since it's tucked away in the count already
        if( k>0 )
          intLvls[j] = Arrays.copyOf(lvls,k);
        ++j;
      }
      int preIgnoredCnt=ignoredCnt;
      for(int k=0;k<intLvls.length;++k)
        if( null==intLvls[k] ) { ignoredCols[ignoredCnt++] = k+_cats; }
      if( ignoredCnt > preIgnoredCnt ) {  // got more ignored, trim out the nulls
        int[][] is = new int[_interactionVecs.length - (ignoredCnt-preIgnoredCnt)][];
        int y=0;
        for (int[] intLvl : intLvls)
          if (intLvl != null)
            is[y++] = intLvl;
        intLvls=is;
      }
    }

    // now numerics
    prev=j=_interactionVecs==null?0:_interactionVecs.length;
    for(;i<cols.length;++i){
      int numsToIgnore = (cols[i]-_numOffsets[j]);
      for(int k=0;k<numsToIgnore;++k){
        ignoredCols[ignoredCnt++] = _cats+prev++;
        ++j;
      }
      prev = ++j;
    }
    for(int k = prev; k < _nums; ++k)
      ignoredCols[ignoredCnt++] = k+_cats;
    Frame f = new Frame(_adaptedFrame.names().clone(),_adaptedFrame.vecs().clone());
    if(ignoredCnt > 0) f.remove(Arrays.copyOf(ignoredCols,ignoredCnt));
    assert catLvls.length <= f.numCols():"cats = " + catLvls.length + " numcols = " + f.numCols();
    double [] normSub = null;
    double [] normMul = null;
    int id = Arrays.binarySearch(cols,numStart());
    if(id < 0) id = -id-1;
    int nnums = cols.length - id - hasIcpt;
    int off = numStart();
    if(_normSub != null) {
      normSub = new double[nnums];
      for(int k = id; k < (id + nnums); ++k)
        normSub[k-id] = _normSub[cols[k]-off];
    }
    if(_normMul != null) {
      normMul = new double[nnums];
      for(int k = id; k < (id + nnums); ++k)
        normMul[k-id] = _normMul[cols[k]-off];
    }
    DataInfo dinfo = new DataInfo(this,f,normMul,normSub,catLvls,intLvls,catModes,cols);
    dinfo._nums=f.numCols()-dinfo._cats - dinfo._responses - (dinfo._offset?1:0) - (dinfo._weights?1:0) - (dinfo._fold?1:0);
    dinfo._numMeans=new double[nnums];
    for(int k=id; k < (id+nnums);++k )
      dinfo._numMeans[k-id] = _numMeans[cols[k]-off];
    return dinfo;
  }

  public void updateWeightedSigmaAndMean(double [] sigmas, double [] mean) {
    int sub = numNums() - _nums;
    if(_predictor_transform.isSigmaScaled()) {
      if(sigmas.length+(sub) != _normMul.length)  // numNums() - _nums  checks for interactions (numNums() > _nums in the case of numerical interactions)
        throw new IllegalArgumentException("Length of sigmas does not match number of scaled columns.");
      for(int i = 0; i < _normMul.length; ++i)
        _normMul[i] = i<sub?_normMul[i]:(sigmas[i-sub] != 0?1.0/sigmas[i-sub]:1);
    }
    if(_predictor_transform.isMeanAdjusted()) {
      if(mean.length+(sub) != _normSub.length)  // numNums() - _nums  checks for interactions (numNums() > _nums in the case of numerical interactions)
        throw new IllegalArgumentException("Length of means does not match number of scaled columns.");
      for(int i=0;i<_normSub.length;++i)
        _normSub[i] = i<sub?_normSub[i]:mean[i-sub];
    }
  }
  public void updateWeightedSigmaAndMeanForResponse(double [] sigmas, double [] mean) {
    if(_response_transform.isSigmaScaled()) {
      if(sigmas.length != _normRespMul.length)
        throw new IllegalArgumentException("Length of sigmas does not match number of scaled columns.");
      for(int i = 0; i < sigmas.length; ++i)
        _normRespMul[i] = sigmas[i] != 0?1.0/sigmas[i]:1;
    }
    if(_response_transform.isMeanAdjusted()) {
      if(mean.length != _normRespSub.length)
        throw new IllegalArgumentException("Length of means does not match number of scaled columns.");
      System.arraycopy(mean,0,_normRespSub,0,mean.length);
    }
  }

  private void setTransform(TransformType t, double [] normMul, double [] normSub, int vecStart, int n) {
    int idx=0; // idx!=i when interactions are in play, otherwise, it's just 'i'
    for (int i = 0; i < n; ++i) {
      Vec v = _adaptedFrame.vec(vecStart + i);
      boolean isIWV = v instanceof InteractionWrappedVec;
      switch (t) {
        case STANDARDIZE:
          if( isIWV ) {
            InteractionWrappedVec iwv = (InteractionWrappedVec)v;
            for(int offset=0;offset<iwv.expandedLength();++offset) {
              normMul[idx+offset] = iwv.getMul(offset+(iwv._useAllFactorLevels?0:1));
              normSub[idx+offset] = iwv.getSub(offset+(iwv._useAllFactorLevels?0:1));
            }
          } else {
            normMul[idx] = (v.sigma() != 0) ? 1.0 / v.sigma() : 1.0;
            normSub[idx] = v.mean();
          }
          break;
        case NORMALIZE:
          if( isIWV ) throw H2O.unimpl();
          normMul[idx] = (v.max() - v.min() > 0)?1.0/(v.max() - v.min()):1.0;
          normSub[idx] = v.mean();
          break;
        case DEMEAN:
          if (isIWV) {
            InteractionWrappedVec iwv = (InteractionWrappedVec)v;
            for(int offset=0;offset<iwv.expandedLength();++offset) {
              normSub[idx+offset] = iwv.getMeans()[offset];
              normMul[idx+offset] = 1;
            }
          } else {
            normSub[idx] = v.mean();
            normMul[idx] = 1;
          }
          break;
        case DESCALE:
          if( isIWV ) throw H2O.unimpl();
          normMul[idx] = (v.sigma() != 0)?1.0/v.sigma():1.0;
          normSub[idx] = 0;
          break;
        default:
          throw H2O.unimpl();
      }
      assert !Double.isNaN(normMul[idx]);
      assert !Double.isNaN(normSub[idx]);
      idx = isIWV?(idx+nextNumericIdx(i)):(idx+1);
    }
  }
  public void setPredictorTransform(TransformType t){
    _predictor_transform = t;
    if(t == TransformType.NONE) {
      _normMul = null;
      _normSub = null;
    } else {
      _normMul = MemoryManager.malloc8d(numNums());
      _normSub = MemoryManager.malloc8d(numNums());
      setTransform(t,_normMul,_normSub,_cats,_nums);
    }
  }

  public void setResponseTransform(TransformType t){
    _response_transform = t;
    if(t == TransformType.NONE) {
      _normRespMul = null;
      _normRespSub = null;
    } else {
      _normRespMul = MemoryManager.malloc8d(_responses);
      _normRespSub = MemoryManager.malloc8d(_responses);
      setTransform(t,_normRespMul,_normRespSub,_adaptedFrame.numCols()-_responses,_responses);
    }
  }

  public boolean isInteractionVec(int colid) {
    if( null==_interactions && null==_interactionVecs ) return false;
    if( _adaptedFrame!=null )
      return _adaptedFrame.vec(colid) instanceof InteractionWrappedVec;
    else
      return Arrays.binarySearch(_interactionVecs,colid) >= 0;
  }

  /**
   *
   * Get the fully expanded number of predictor columns.
   * Note that this value does not include:
   *  response column(s)
   *  weight column
   *  offset column
   *  fold column
   *
   * @return expanded number of columns in the underlying frame
   */
  public final int fullN()     { return numNums() + numCats();      }
  public final int largestCat(){ return _cats > 0?_catOffsets[1]:0; }
  public final int numStart()  { return _catOffsets[_cats];         }
  public final int numCats()   { return _catOffsets[_cats];         }
  public final int numNums()   {
    int nnums=0;
    if( _numOffsets==null && _intLvls.length>0 ) {  // filtered columns?
      for (int[] _intLvl : _intLvls) nnums += _intLvl==null?0:_intLvl.length-1;  // minus 1 for the fact that we get a +1 from the dummy interaction vec sitting in the frame!
      return nnums+_nums;
    }
    return _interactionVecs!=null&&_numOffsets!=null?(_numOffsets[_numOffsets.length-1]-numStart()):_nums;
  }

  /**
   * Get the next expanded number-column index.
   */
  public final int nextNumericIdx(int currentColIdx) {
    if( _numOffsets==null ) {
      if( currentColIdx < _interactionVecs.length ) {  // currently sitting on an interaction vec, return the number of levels
        return _intLvls[currentColIdx].length;
      } else
        return 1;
    }
    if( currentColIdx+1 >= _numOffsets.length ) return fullN() - _numOffsets[currentColIdx];
    return _numOffsets[currentColIdx+1] - _numOffsets[currentColIdx];
  }
  public final String[] coefNames() {
    if (_coefNames != null) return _coefNames; // already computed
    int k = 0;
    final int n = fullN(); // total number of columns to compute
    String [] res = new String[n];
    final Vec [] vecs = _adaptedFrame.vecs();

    // first do all of the expanded categorical names
    for(int i = 0; i < _cats; ++i) {
      for (int j = (_useAllFactorLevels || vecs[i] instanceof InteractionWrappedVec) ? 0 : 1; j < vecs[i].domain().length; ++j) {
        int jj = getCategoricalId(i, j);
        if(jj < 0)
          continue;
        res[k++] = _adaptedFrame._names[i] + "." + vecs[i].domain()[j];
      }
      if (_catMissing[i] && getCategoricalId(i, -1) >=0)
        res[k++] = _adaptedFrame._names[i] + ".missing(NA)";
      if( vecs[i] instanceof InteractionWrappedVec ) {
        InteractionWrappedVec iwv = (InteractionWrappedVec)vecs[i];
        if( null!=iwv.missingDomains() ) {
          for(String s: iwv.missingDomains() )
            res[k++] = s+".missing(NA)";
        }
      }
    }
    // now loop over the numerical columns, collecting up any expanded InteractionVec names
    if( _interactions==null ) {
      final int nums = n-k;
      System.arraycopy(_adaptedFrame._names, _cats, res, k, nums);
    } else {
      for (int i = 0; i <= _nums; i++) {
        InteractionWrappedVec v;
        if( i+_cats >= n || k >=n ) break;
        if (vecs[i+_cats] instanceof InteractionWrappedVec && ((v = (InteractionWrappedVec) vecs[i+_cats]).domain() != null)) { // in this case, get the categoricalOffset
          for (int j = v._useAllFactorLevels?0:1; j < v.domain().length; ++j) {
            if (getCategoricalIdFromInteraction(_cats+i, j) < 0)
              continue;
            res[k++] = _adaptedFrame._names[i+_cats] + "." + v.domain()[j];
          }
        } else
          res[k++] = _adaptedFrame._names[i+_cats];
      }
    }
    _coefNames = res;
    return res;
  }

  // Return permutation matrix mapping input names to adaptedFrame colnames
  public int[] mapNames(String[] names) {
    assert names.length == _adaptedFrame._names.length : "Names must be the same length!";
    int[] idx = new int[names.length];
    Arrays.fill(idx, -1);

    for(int i = 0; i < _adaptedFrame._names.length; i++) {
      for(int j = 0; j < names.length; j++) {
        if( names[j].equals(_adaptedFrame.name(i)) ) {
          idx[i] = j; break;
        }
      }
    }
    return idx;
  }

  /**
   * Undo the standardization/normalization of numerical columns
   * @param in input values
   * @param out output values (can be the same as input)
   */
  public final void unScaleNumericals(double[] in, double[] out) {
    if (_nums == 0) return;
    assert (in.length == out.length);
    assert (in.length == fullN());
    for (int k=numStart(); k < fullN(); ++k) {
      double m = _normMul == null ? 1f : _normMul[k-numStart()];
      double s = _normSub == null ? 0f : _normSub[k-numStart()];
      out[k] = in[k] / m + s;
    }
  }

  public final class Row extends Iced {
    public boolean predictors_bad;        // should the row be skipped (GLM skip NA for example)
    public boolean response_bad;
    public boolean isBad(){return predictors_bad || response_bad;}
    public double [] numVals;  // the backing data of the row
    public double [] response;
    public int    [] numIds;   // location of next sparse value
    public int    [] binIds;   // location of categorical
    public long      rid;      // row number (sometimes within chunk, or absolute)
    public int       cid;      // categorical id
    public int       nBins;    // number of enum    columns (not expanded)
    public int       nNums;    // number of numeric columns (not expanded)
    public double    offset = 0;
    public double    weight = 1;

    public final boolean isSparse(){return numIds != null;}


    public double[] mtrxMul(double [][] m, double [] res){
       for(int i = 0; i < m.length; ++i)
        res[i] = innerProduct(m[i],false);
      return res;
    }

    public Row(boolean sparse, int nNums, int nBins, int nresponses, int i, long start) {
      binIds = MemoryManager.malloc4(nBins);
      numVals = MemoryManager.malloc8d(nNums);
      response = MemoryManager.malloc8d(nresponses);
      if(sparse)
        numIds = MemoryManager.malloc4(nNums);
      this.nNums = sparse?0:nNums;
      cid = i;
      rid = start + i;
    }

    public Row(boolean sparse, double[] numVals, int[] binIds, double[] response, int i, long start) {
      int nNums = numVals == null ? 0:numVals.length;
      this.numVals = numVals;
      if(sparse)
        numIds = MemoryManager.malloc4(nNums);
      this.nNums = sparse ? 0:nNums;
      this.nBins = binIds == null ? 0:binIds.length;
      this.binIds = binIds;
      this.response = response;
      cid = i;
      rid = start + i;
    }

    public Row(double [] nums) {
      numVals = nums;
      nNums = nums.length;
    }
    public double response(int i) {return response[i];}

    public double get(int i) {
      int off = numStart();
      if(i >= off) { // numbers
        if(numIds == null)
          return numVals[i-off];
        int j = Arrays.binarySearch(numIds,0,nNums,i);
        return j >= 0?numVals[j]:0;
      } else { // categoricals
        int j = Arrays.binarySearch(binIds,0,nBins,i);
        return j >= 0?1:0;
      }
    }

    public void addNum(int id, double val) {
      if(numIds.length == nNums) {
        int newSz = Math.max(4,numIds.length + (numIds.length >> 1));
        numIds = Arrays.copyOf(numIds, newSz);
        numVals = Arrays.copyOf(numVals, newSz);
      }
      int i = nNums++;
      numIds[i] = id;
      numVals[i] = val;
    }

    /*
    This method will perform an inner product of rows.  It will be able to handle categorical data
    as well as numerical data.  However, the two rows must have exactly the same column types.  This
    is used in a situation where the rows are coming from the same dataset.
     */
    public final double dotSame(Row rowj) {
      // nums
      double elementij = 0.0;
      for(int i = 0; i < this.nNums; ++i)  {
        elementij += this.numVals[i]*rowj.numVals[i]; // multiply numerical parts of columns
      }

      // cat X cat
      if (this.binIds.length > 0) { // categorical columns exists
        for (int j = 0; j < this.nBins; ++j) {
          if (this.binIds[j] == rowj.binIds[j]) {
            elementij += 1;
          }
        }
      }
      return elementij*this.weight*rowj.weight;
    }

    public final double innerProduct(double [] vec) { return innerProduct(vec,false);}
    public final double innerProduct(double [] vec, boolean icptFirst) {
      double res = 0;
      int off = 0;
      if(icptFirst) {
        off = 1;
        res = vec[0];
      }
      int numStart = off + numStart();

      for(int i = 0; i < nBins; ++i)
        res += vec[off+binIds[i]];
      if(numIds == null) {
        for (int i = 0; i < numVals.length; ++i)
          res += numVals[i] * vec[numStart + i];
      } else {
        for (int i = 0; i < nNums; ++i)
          res += numVals[i] * vec[off+numIds[i]];
      }
      if(_intercept && !icptFirst)
        res += vec[vec.length-1];
      return res;
    }
    public final double innerProduct(DataInfo.Row row) {
      assert !_intercept;
      assert numIds == null;

      double res = 0;
      for (int i = 0; i < nBins; ++i)
        if (binIds[i] == row.binIds[i])
          res += 1;
      for (int i = 0; i < numVals.length; ++i)
        res += numVals[i] * row.numVals[i];

      return res;
    }
    public final double twoNormSq() {
      assert !_intercept;
      assert numIds == null;

      double res = nBins;
      for (double v : numVals)
        res += v * v;

      return res;
    }

    /**
     * 
     * @param vec store the coefficients of the GLM for each class, shortened and only include active columns
     * @param activeCols column indices of predictors that are active.  Expanded cat columns
     * @return
     */
    public final double innerProduct(double [] vec, int[] activeCols) {
      double res = 0;
      int numStart = numStart();  // numerical column coefficient index start with expanded cat columns
      int catColInd = 0;
      int colInd = 0;
      if (_cats > 0) {
        for (int i : activeCols) { // take care of cat columns here
          if (i >= _catOffsets[catColInd + 1])
            catColInd++;
          if (catColInd >= _cats)
            break;
          if (i == binIds[catColInd]) {
            res += vec[colInd];
          }
          colInd++;
        }
      }
      int actColLen = activeCols.length-1;  // last one is the intercept
      for (int colidx = colInd; colidx < actColLen; colidx++) {
        res += numVals[activeCols[colidx] - numStart] * vec[colidx];
      }
      if(_intercept)
        res += vec[actColLen];
      return res;
    }

    public double[] expandCats() {
      if(isSparse() || _responses > 0) throw H2O.unimpl();

      int N = fullN();
      int numStart = numStart();
      double[] res = new double[N + (_intercept ? 1:0)];

      for(int i = 0; i < nBins; ++i)
        res[binIds[i]] = 1;
      if(numIds == null) {
        System.arraycopy(numVals,0,res,numStart,numVals.length);
      } else {
        for(int i = 0; i < nNums; ++i)
          res[numIds[i]] = numVals[i];
      }
      if(_intercept)
        res[res.length-1] = 1;
      return res;
    }
    
    

    public String toString() {
      return this.rid + Arrays.toString(Arrays.copyOf(binIds,nBins)) + ", " + Arrays.toString(numVals);
    }
    public void setResponse(int i, double z) {response[i] = z;}

    public void standardize(double[] normSub, double[] normMul) {
      if(numIds == null){
        for(int i = 0; i < numVals.length; ++i)
          numVals[i] = (numVals[i] - normSub[i])*normMul[i];
      } else
        for(int i = 0; i < nNums; ++i) {
          int j = numIds[i];
          numVals[i] = (numVals[i] - normSub[j])*normMul[j];
        }
    }

    public Row deepClone() {
      Row cloned = (Row) clone();
      cloned.numVals = numVals.clone();
      if (numIds != null)
        cloned.numIds = numIds.clone();
      cloned.response = response.clone();
      cloned.binIds = binIds.clone();
      return cloned;
    }
    
    public void addToArray(double scale, double []res) {
      for (int i = 0; i < nBins; i++)
        res[binIds[i]] += scale;
      int numstart = numStart();
      if (numIds != null) {
        for (int i = 0; i < nNums; ++i)
          res[numIds[i]] += scale * numVals[i];
      } else for (int i = 0; i < numVals.length; ++i)
        if (numVals[i] != 0)
          res[numstart + i] += scale * numVals[i];
      if (_intercept)
        res[res.length - 1] += scale;
    }
  }


  public final int getCategoricalId(int cid, double val) {
    if(Double.isNaN(val)) return getCategoricalId(cid, -1);
    int ival = (int)val;
    if(ival != val) throw new IllegalArgumentException("Categorical id must be an integer or NA (missing).");
    return getCategoricalId(cid,ival);
  }
  /**
   * Get the offset into the expanded categorical
   * @param cid the column id
   * @param val the integer representation of the categorical level
   * @return offset into the fullN set of columns
   */
  public final int getCategoricalId(int cid, int val) {
    boolean isIWV = isInteractionVec(cid);
    if(val == -1) { // NA
      val = _catNAFill[cid];
    }

    if (!_useAllFactorLevels && !isIWV) {  // categorical interaction vecs drop reference level in a special way
      val = val - 1;
    }
    if(val < 0) return -1; // column si to be skipped
    int [] offs = fullCatOffsets();
    int expandedVal = val + offs[cid];
    if(expandedVal >= offs[cid+1]) {  // previously unseen level
      assert _valid : "Categorical value out of bounds, got " + val + ", next cat starts at " + fullCatOffsets()[cid + 1];
      if(_skipMissing)
        return -1;
      val = _catNAFill[cid];
      
      if (!_useAllFactorLevels && !isIWV) {  // categorical interaction vecs drop reference level in a special way
        val = val - 1;
      }
    }
    if (_catMap != null && _catMap[cid] != null) {  // some levels are ignored?
      val = _catMap[cid][val];
      assert _useAllFactorLevels;
    }
    return val < 0?-1:val + _catOffsets[cid];
  }

  public final int getCategoricalIdFromInteraction(int cid, int val) {
    InteractionWrappedVec v = (InteractionWrappedVec) _adaptedFrame.vec(cid);
    if (v.isCategorical())
      return getCategoricalId(cid, val);
    assert v.domain() != null : "No domain levels found for interactions! cid: " + cid + " val: " + val;
    cid -= _cats;
    if (! v._useAllFactorLevels)
      val--;
    assert val >= 0;
    if (val >= _numOffsets[cid+1]) { // previously unseen interaction (aka new domain level)
      assert _valid : "interaction value out of bounds, got " + val + ", next cat starts at " + _numOffsets[cid+1];
      val = v.mode();
    }
    if( cid < _intLvls.length && _intLvls[cid]!=null ) {
      assert _useAllFactorLevels; // useAllFactorLevels has to be defined on a global level (not just for the interaction)
      val = Arrays.binarySearch(_intLvls[cid],val);
    }
    return val < 0?-1:val+_numOffsets[cid];
  }


  public final Row extractDenseRow(Chunk[] chunks, int rid, Row row) {
    row.predictors_bad = false;
    row.response_bad = false;
    row.rid = rid + chunks[0].start();
    row.cid = rid;
    if(_weights)
      row.weight = chunks[weightChunkId()].atd(rid);
    if(row.weight == 0) return row;
    if (_skipMissing) {
      int N = _cats + _nums;
      for (int i = 0; i < N; ++i)
        if (chunks[i].isNA(rid)) {
          row.predictors_bad = true;
          return row;
        }
    }
    int nbins = 0;
    for (int i = 0; i < _cats; ++i) {
      int cid = getCategoricalId(i,chunks[i].isNA(rid)? _catNAFill[i]:(int)chunks[i].at8(rid));
      if(cid >= 0)
        row.binIds[nbins++] = cid;
    }
    row.nBins = nbins;
    final int n = _nums;
    int numValsIdx=0; // since we're dense, need a second index to track interaction nums
    for( int i=0;i<n;i++) {
      if( isInteractionVec(_cats + i) ) {  // categorical-categorical interaction is handled as plain categorical (above)... so if we have interactions either v1 is categorical, v2 is categorical, or neither are categorical
        InteractionWrappedVec iwv = (InteractionWrappedVec)_adaptedFrame.vec(_cats+i);
        int interactionOffset = getInteractionOffset(chunks,_cats+i,rid);
        for(int offset=0;offset<iwv.expandedLength();++offset) {
          if( i < _intLvls.length && _intLvls[i]!=null && Arrays.binarySearch(_intLvls[i],offset) < 0 ) continue; // skip the filtered out interactions
          double d=0;
          if( offset==interactionOffset ) d=chunks[_cats + i].atd(rid);
          if( Double.isNaN(d) )
            d = _numMeans[numValsIdx];
          if( _normMul != null && _normSub != null )
            d = (d - _normSub[numValsIdx]) * _normMul[numValsIdx];
          row.numVals[numValsIdx++]=d;
        }
      } else {
        double d = chunks[_cats + i].atd(rid); // can be NA if skipMissing() == false
        if (Double.isNaN(d))
          d = _numMeans[numValsIdx];
        if (_normMul != null && _normSub != null)
          d = (d - _normSub[numValsIdx]) * _normMul[numValsIdx];
        row.numVals[numValsIdx++] = d;
      }
    }
    for (int i = 0; i < _responses; ++i) {
      row.response[i] = chunks[responseChunkId(i)].atd(rid);
      if(Double.isNaN(row.response[i])) {
        row.response_bad = true;
        break;
      }
      if (_normRespMul != null)
        row.response[i] = (row.response[i] - _normRespSub[i]) * _normRespMul[i];
    }
    if(_offset)
      row.offset = chunks[offsetChunkId()].atd(rid);
    return row;
  }
  public int getInteractionOffset(Chunk[] chunks, int cid, int rid) {
    boolean useAllFactors = ((InteractionWrappedVec)chunks[cid].vec())._useAllFactorLevels;
    InteractionWrappedVec.InteractionWrappedChunk c = (InteractionWrappedVec.InteractionWrappedChunk)chunks[cid];
    if(      c._c1IsCat ) return (int)c._c[0].at8(rid)-(useAllFactors?0:1);
    else if( c._c2IsCat ) return (int)c._c[1].at8(rid)-(useAllFactors?0:1);
    return 0;
  }
  public Vec getWeightsVec(){return _adaptedFrame.vec(weightChunkId());}
  public Vec getOffsetVec(){return _adaptedFrame.vec(offsetChunkId());}
  public Row newDenseRow(){return new Row(false,numNums(),_cats,_responses,0,0);}  // TODO: _nums => numNums since currently extracting out interactions into dense
  public Row newDenseRow(double[] numVals, long start) {
    return new Row(false, numVals, null, null, 0, start);
  }

  public final class Rows {
    public final int _nrows;
    private final Row _denseRow;
    private final Row [] _sparseRows;
    public final boolean _sparse;
    private final Chunk [] _chks;

    private Rows(Chunk [] chks, boolean sparse) {
      _nrows = chks[0]._len;
      _sparse = sparse;
      long start = chks[0].start();
      if(sparse) {
        _denseRow = null;
        _chks = null;
        _sparseRows = extractSparseRows(chks);
      } else {
        _denseRow = DataInfo.this.newDenseRow();
        _chks = chks;
        _sparseRows = null;
      }
    }
    public Row row(int i) {return _sparse?_sparseRows[i]:extractDenseRow(_chks,i,_denseRow);}
  }

  public Rows rows(Chunk [] chks) {
    int cnt = 0;
    for(Chunk c:chks)
      if(c.isSparseZero())
        ++cnt;
    return rows(chks,cnt > (chks.length >> 1));
  }
  public Rows rows(Chunk [] chks, boolean sparse) {return new Rows(chks,sparse);}

  /**
   * Extract (sparse) rows from given chunks.
   * Note: 0 remains 0 - _normSub of DataInfo isn't used (mean shift during standarization is not reverted) - UNLESS offset is specified (for GLM only)
   * Essentially turns the dataset 90 degrees.
   * @param chunks - chunk of dataset
   * @return array of sparse rows
   */
  public final Row[] extractSparseRows(Chunk [] chunks) {
    Row[] rows = new Row[chunks[0]._len];
    long startOff = chunks[0].start();
    for (int i = 0; i < rows.length; ++i) {
      rows[i] = new Row(true, Math.min(_nums, 16), _cats, _responses, i, startOff);  // if sparse, _nums is the correct number of nonzero values! i.e., do not use numNums()
      rows[i].rid = chunks[0].start() + i;
      if(_offset)  {
        rows[i].offset = chunks[offsetChunkId()].atd(i);
        if(Double.isNaN(rows[i].offset)) {
          rows[i].predictors_bad = true;
          continue;
        }
      }
      if(_weights) {
        rows[i].weight = chunks[weightChunkId()].atd(i);
        if(Double.isNaN(rows[i].weight))
          rows[i].predictors_bad = true;
      }
    }
    // categoricals
    for (int i = 0; i < _cats; ++i) {
      for (int r = 0; r < chunks[0]._len; ++r) {
        Row row = rows[r];
        boolean isMissing = chunks[i].isNA(r);
        if(_skipMissing && isMissing){
          row.predictors_bad = true;
          continue;
        }         
        int cid = getCategoricalId(i,isMissing? -1:(int)chunks[i].at8(r));
        if(cid >=0)
          row.binIds[row.nBins++] = cid;
      }
    }
    // generic numbers + interactions
    int interactionOffset=0;
    for (int cid = 0; cid < _nums; ++cid) {
      Chunk c = chunks[_cats + cid];
      int oldRow = -1;
      if (c instanceof InteractionWrappedVec.InteractionWrappedChunk) {  // for each row, only 1 value in an interaction is 'hot' all other values are off (i.e., are 0)
        InteractionWrappedVec iwv = (InteractionWrappedVec)c.vec();
        for(int r=0;r<c._len;++r) {  // the vec is "vertically" dense and "horizontally" sparse (i.e., every row has one, and only one, value)
          Row row = rows[r];
          if( c.isNA(r) && _skipMissing)
            row.predictors_bad = true;
          if(row.predictors_bad) continue;
          int cidVirtualOffset = getInteractionOffset(chunks,_cats+cid,r);  // the "virtual" offset into the hot-expanded interaction
          if( cidVirtualOffset>=0 ) {
            if( cid < _intLvls.length && _intLvls[cid]!=null && Arrays.binarySearch(_intLvls[cid],cidVirtualOffset) < 0 ) continue; // skip the filtered out interactions
            if( c.atd(r)==0 ) continue;
            double d = c.atd(r);
            if( Double.isNaN(d) )
              d = _numMeans[interactionOffset+cidVirtualOffset];  // FIXME: if this produces a "true" NA then should sub with mean? with?
            if (_normMul != null)
              d *= _normMul[interactionOffset+cidVirtualOffset];
            row.addNum(numStart()+interactionOffset+cidVirtualOffset, d);
          }
        }
        interactionOffset+=nextNumericIdx(cid);
      } else {
        for (int r = c.nextNZ(-1, _imputeMissing); r < c._len; r = c.nextNZ(r, _imputeMissing)) {
          if (c.atd(r) == 0) continue;
          assert r > oldRow;
          oldRow = r; Row row = rows[r];
          if (c.isNA(r) && _skipMissing)
            row.predictors_bad = true;
          if (row.predictors_bad) continue;
          double d = c.atd(r);
          if (Double.isNaN(d))
            d = _numMeans[cid];
          if (_normMul != null)
            d *= _normMul[interactionOffset];
          row.addNum(numStart()+interactionOffset,d);
        }
        interactionOffset++;
      }
    }
    // response(s)
    for (int i = 1; i <= _responses; ++i) {
      int rid = responseChunkId(i-1);
      Chunk rChunk = chunks[rid];
      for (int r = 0; r < chunks[0]._len; ++r) {
        Row row = rows[r];
        row.response[i-1] = rChunk.atd(r);
        if(Double.isNaN(row.response[i-1])) {
          row.response_bad = true;
        }
        if (_normRespMul != null) {
          row.response[i-1] = (row.response[i-1] - _normRespSub[i-1]) * _normRespMul[i-1];
        }
      }
    }
    return rows;
  }

  public DataInfo scoringInfo(String[] names, Frame adaptFrame) {
    return scoringInfo(names, adaptFrame, -1, true);
  }

  /**
   * Creates a scoringInfo from a DataInfo instance created during model training
   * @param names column names
   * @param adaptFrame adapted frame
   * @param nResponses number of responses (-1 indicates autodetect: 0/1 based on presence of a single response)
   * @param fixIVW whether to force global useFactorLevels flag to InteractionWrappedVecs (GLM behavior)
   * @return
   */
  public DataInfo scoringInfo(String[] names, Frame adaptFrame, int nResponses, boolean fixIVW) {
    DataInfo res = IcedUtils.deepCopy(this);
    res._normMul = null;
    res._normRespSub = null;
    res._normRespMul = null;
    res._normRespSub = null;
    res._predictor_transform = TransformType.NONE;
    res._response_transform = TransformType.NONE;
    res._adaptedFrame = adaptFrame;
    res._weights = _weights && adaptFrame.find(names[weightChunkId()]) != -1;
    res._offset = _offset && adaptFrame.find(names[offsetChunkId()]) != -1;
    res._fold = _fold && adaptFrame.find(names[foldChunkId()]) != -1;
    if (nResponses != -1) {
      res._responses = nResponses;
    } else {
      int resId = adaptFrame.find(names[responseChunkId(0)]);
      if (resId == -1 || adaptFrame.vec(resId).isBad())
        res._responses = 0;
      else // NOTE: DataInfo can have extra columns encoded as response, e.g. helper columns when doing Multinomail IRLSM, don't need those for scoring!.
        res._responses = 1;
    }
    res._valid = true;
    res._interactions=_interactions;
    res._interactionSpec=_interactionSpec;

    if (fixIVW) {
      // ensure that vecs are in the DKV, may have been swept up in the Scope.exit call
      for (Vec v : res._adaptedFrame.vecs())
        if (v instanceof InteractionWrappedVec) {
          ((InteractionWrappedVec) v)._useAllFactorLevels = _useAllFactorLevels;
          ((InteractionWrappedVec) v)._skipMissing = _skipMissing;
          DKV.put(v);
        }
    }
    return res;
  }

  /**
   * Creates a DataInfo for scoring on a test Frame from a DataInfo instance created during model training
   * This is a lightweight version of the method only usable for models that don't use advanced features of DataInfo (eg. interaction terms)
   * @return DataInfo for scoring
   */
  public DataInfo scoringInfo() {
    DataInfo res = IcedUtils.deepCopy(this);
    res._valid = true;
    return res;
  }

}
