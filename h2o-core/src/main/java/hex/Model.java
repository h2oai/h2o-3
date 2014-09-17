package hex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import water.*;
import water.api.ModelSchema;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.TransfVec;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

/**
 * A Model models reality (hopefully).
 * A model can be used to 'score' a row (make a prediction), or a collection of
 * rows on any compatible dataset - meaning the row has all the columns with the
 * same names as used to build the mode and any enum (categorical) columns can
 * be adapted.
 */
public abstract class Model<M extends Model<M,P,O>, P extends Model.Parameters<M,P,O>, O extends Model.Output<M,P,O>> extends Lockable<M> {
  Model( Key selfkey ) { super(selfkey); }

  public enum ModelCategory {
    Unknown,
    Binomial,
    Multinomial,
    Regression,
    Clustering
  }

  /**
   *
   * Needs to be set correctly otherwise eg scoring does not work.
   * @return true if there was a response column used during training.
   */
  public abstract boolean isSupervised();

  /**
   * Model-specific parameter class.  Each model sub-class contains an instance of one of
   * these containing its builder parameters, with model-specific parameters.
   * E.g. KMeansModel extends Model & has a KMeansParameters extending Model.Parameters;
   * sample parameters include K, whether or not to normalize, max iterations and the
   * initial random seed.
   */
  public abstract static class Parameters<M extends Model<M,P,O>, P extends Parameters<M,P,O>, O extends Output<M,P,O>> extends Iced {
    public Key<Frame> _training_frame; // Frame the Model is trained on
    public Key<Frame> _validation_frame; // Frame the Model is validated on, if any
    public String response_column;   // column name
    public String[] ignored_columns; // column names to ignore for training

    // TODO: move to utils class
    protected Frame sanityCheckFrameKey(Key key, String description) {
      if (null == key)
        throw new IllegalArgumentException(description + " key must be non-null.");
      Value v = DKV.get(key);
      if (null == v)
        throw new IllegalArgumentException(description + " key not found: " + key);
      if (! v.isFrame())
        throw new IllegalArgumentException(description + " key points to a non-Frame object in the KV store: " + key);
      Frame frame = v.get();
      if (frame.numCols() <= 1)
        throw new IllegalArgumentException(description + " must have at least 2 features (incl. response).");
      return frame;
    }
  }

  public P _parms; // TODO: move things around so that this can be protected

  public String [] _warnings = new String[0];

  public void addWarning(String s){
    _warnings = Arrays.copyOf(_warnings,_warnings.length+1);
    _warnings[_warnings.length-1] = s;
  }

  /**
   * Model-specific output class.  Each model sub-class contains an instance of one of
   * these containing its "output": the pieces of the model needed for scoring.
   * E.g. KMeansModel has a KMeansOutput extending Model.Output which contains the
   * clusters.  The output also includes the names, domains and other fields which are
   * determined at training time.
   */
  public abstract static class Output<M extends Model<M,P,O>, P extends Parameters<M,P,O>, O extends Output<M,P,O>> extends Iced {
    /** Columns used in the model and are used to match up with scoring data
     *  columns.  The last name is the response column name. */
    public String _names[];
    /** Returns number of input features (OK for most supervised methods, need to override for unsupervised!) */
    public int nfeatures() { return _names.length - 1; }

    /** Categorical/factor/enum mappings, per column.  Null for non-enum cols.
     *  The last column holds the response col enums.  */
    public String _domains[][];

    /** The names of all the columns, including the response column (which comes last). */
    public String[] allNames() { return _names; }
    /** The name of the response column (which is always the last column). */
    public String responseName() { return   _names[  _names.length-1]; }
    /** The names of the levels for an enum (categorical) response column. */
    public String[] classNames() { return _domains[_domains.length-1]; }
    /** Is this model a classification model? (v. a regression or clustering model) */
    public boolean isClassifier() { return classNames() != null ; }
    public int nclasses() {
      String cns[] = classNames();
      return cns==null ? 1 : cns.length;
    }

    // Note: Clustering algorithms MUST redefine this method to return ModelCategory.Clustering:
    public ModelCategory getModelCategory() {
      return (isClassifier() ?
              (nclasses() > 2 ? ModelCategory.Multinomial : ModelCategory.Binomial) :
              ModelCategory.Regression);
    }
  } // Output

  public O _output; // TODO: move things around so that this can be protected

  /**
   * Model-specific state class.  Each model sub-class contains an instance of one of
   * these containing its internal state: all the data that's required to, for example,
   * reload the state of the model to continue training.
   * TODO: use this!
  public abstract static class State<M extends Model<M,S>, S extends State<M,S>> extends Iced {
  }
  // TODO: make this an instance of a *parameterized* State class. . .
  State _state;
  public State getState() { return _state; }
   */

  private UniqueId uniqueId = null;

  /** The start time in mS since the epoch for model training. */
  public long training_start_time = 0L;

  /** The duration in mS for model training. */
  public long training_duration_in_ms = 0L;

  /**
   * Externally visible default schema
   * TODO: this is in the wrong layer: the internals should not know anything about the schemas!!!
   * This puts a reverse edge into the dependency graph.
   */
  public abstract ModelSchema schema();

  /** Constructor from frame: Strips out the Vecs to just the names needed
   *  to match columns later for future datasets.  */
  public Model( Key selfKey, Frame fr, P parms, O output ) {
    this(selfKey,fr.names(),fr.domains(),parms,output);
  }

  /** Full constructor */
  public Model( Key selfKey, String names[], String domains[][], P parms, O output) {
    super(selfKey);

    assert output != null;
    _output = output;

    this.uniqueId = new UniqueId(_key);

    if( domains == null ) domains=new String[names.length+1][];
    assert domains.length==names.length;
    assert names.length > 1;
    assert names[names.length-1] != null; // Have a valid response-column name?
    _output._names   = names;
    _output._domains = domains;

    assert parms != null;
    _parms = parms;
  }

  public UniqueId getUniqueId() {
    return this.uniqueId;
  }

  public void start_training(long training_start_time) {
    Log.info("setting training_start_time to: " + training_start_time + " for Model: " + this._key.toString() + " (" + this.getClass().getSimpleName() + "@" + System.identityHashCode(this) + ")");

    final long t = training_start_time;
    new TAtomic<Model>() {
      @Override public Model atomic(Model m) {
          if (m != null) {
            m.training_start_time = t;
          } return m;
      }
    }.invoke(_key);
    this.training_start_time = training_start_time;
  }
  public void start_training(Model previous) {
    training_start_time = System.currentTimeMillis();
    Log.info("setting training_start_time to: " + training_start_time + " for Model: " + this._key.toString() + " (" + this.getClass().getSimpleName() + "@" + System.identityHashCode(this) + ") [checkpoint case]");
    if (null != previous)
      training_duration_in_ms += previous.training_duration_in_ms;

    final long t = training_start_time;
    final long d = training_duration_in_ms;
    new TAtomic<Model>() {
      @Override public Model atomic(Model m) {
          if (m != null) {
            m.training_start_time = t;
            m.training_duration_in_ms = d;
          } return m;
      }
    }.invoke(_key);
  }
  public void stop_training() {
    training_duration_in_ms += (System.currentTimeMillis() - training_start_time);
    Log.info("setting training_duration_in_ms to: " + training_duration_in_ms + " for Model: " + this._key.toString() + " (" + this.getClass().getSimpleName() + "@" + System.identityHashCode(this) + ")");

    final long d = training_duration_in_ms;
    new TAtomic<Model>() {
      @Override public Model atomic(Model m) {
          if (m != null) {
            m.training_duration_in_ms = d;
          } return m;
      }
    }.invoke(_key);
  }

  /** Bulk score for given <code>fr</code> frame.
   * The frame is always adapted to this model.
   *
   * @param fr frame to be scored
   * @return frame holding predicted values
   *
   * @see #score(Frame, boolean)
   */
  public Frame score(Frame fr) {
    return score(fr, true);
  }
  /** Bulk score the frame <code>fr</code>, producing a Frame result; the 1st Vec is the
   *  predicted class, the remaining Vecs are the probability distributions.
   *  For Regression (single-class) models, the 1st and only Vec is the
   *  prediction value.
   *
   *  The flat <code>adapt</code>
   * @param fr frame which should be scored
   * @param adapt a flag enforcing an adaptation of <code>fr</code> to this model. If flag
   *        is <code>false</code> scoring code expect that <code>fr</code> is already adapted.
   * @return a new frame containing a predicted values. For classification it contains a column with
   *         prediction and distribution for all response classes. For regression it contains only
   *         one column with predicted values.
   */
  public final Frame score(Frame fr, boolean adapt) {
    if (isSupervised()) {
      int ridx = fr.find(_output.responseName());
      if (ridx != -1) { // drop the response for scoring!
        fr = new Frame(fr);
        fr.remove(ridx);
      }
    }
    // Adapt the Frame layout - returns adapted frame and frame containing only
    // newly created vectors
    Frame[] adaptFrms = adapt ? adapt(fr,false) : null;
    // Adapted frame containing all columns - mix of original vectors from fr
    // and newly created vectors serving as adaptors
    Frame adaptFrm = adapt ? adaptFrms[0] : fr;
    // Contains only newly created vectors. The frame eases deletion of these vectors.
    Frame onlyAdaptFrm = adapt ? adaptFrms[1] : null;
    // Invoke scoring
    Frame output = scoreImpl(adaptFrm);
    // Be nice to DKV and delete vectors which i created :-)
    if (adapt) onlyAdaptFrm.delete();
    return output;
  }

  /** Score already adapted frame.
   *
   * @param adaptFrm
   * @return
   */
  private Frame scoreImpl(Frame adaptFrm) {
    if (isSupervised()) {
      int ridx = adaptFrm.find(_output.responseName());
      assert ridx == -1 : "Adapted frame should not contain response in scoring method!";
      assert _output.nfeatures() == adaptFrm.numCols() : "Number of model features " + _output.nfeatures() + " != number of test set columns: " + adaptFrm.numCols();
      assert adaptFrm.vecs().length == _output.nfeatures() : "Scoring data set contains wrong number of columns: " + adaptFrm.vecs().length + " instead of " + _output.nfeatures();
    }

    // Create a new vector for response
    // If the model produces a classification/enum, copy the domain into the
    // result vector.
    int nc = _output.nclasses();
    Vec [] newVecs = new Vec[]{adaptFrm.anyVec().makeZero(_output.classNames())};
    if(nc > 1)
      newVecs = ArrayUtils.join(newVecs,adaptFrm.anyVec().makeZeros(nc));
    String [] names = new String[newVecs.length];
    names[0] = "predict";
    for(int i = 1; i < names.length; ++i)
      names[i] = _output.classNames()[i-1];
    final int num_features = _output.nfeatures();
    new MRTask() {
      @Override public void map( Chunk chks[] ) {
        double tmp [] = new double[num_features];
        float preds[] = new float [_output.nclasses()==1?1:_output.nclasses()+1];
        int len = chks[0].len();
        for( int row=0; row<len; row++ ) {
          float p[] = score0(chks,row,tmp,preds);
          for( int c=0; c<preds.length; c++ )
            chks[num_features+c].set0(row,p[c]);
        }
      }
    }.doAll(ArrayUtils.join(adaptFrm.vecs(),newVecs));
    // Return just the output columns
    return new Frame(names,newVecs);
  }

  /** Single row scoring, on a compatible Frame.  */
  public final float[] score( Frame fr, boolean exact, int row ) {
    double tmp[] = new double[fr.numCols()];
    for( int i=0; i<tmp.length; i++ )
      tmp[i] = fr.vecs()[i].at(row);
    return score(fr.names(),fr.domains(),exact,tmp);
  }

  /** Single row scoring, on a compatible set of data.  Fairly expensive to adapt. */
  public final float[] score( String names[], String domains[][], boolean exact, double row[] ) {
    return score(adapt(names,domains,exact),row,new float[_output.nclasses()]);
  }

  /** Single row scoring, on a compatible set of data, given an adaption vector */
  public final float[] score( int map[][][], double row[], float[] preds ) {
    /*FIXME final int[][] colMap = map[map.length-1]; // Response column mapping is the last array
    assert colMap.length == _output._names.length-1 : " "+Arrays.toString(colMap)+" "+Arrays.toString(_output._names);
    double tmp[] = new double[colMap.length]; // The adapted data
    for( int i=0; i<colMap.length; i++ ) {
      // Column mapping, or NaN for missing columns
      double d = colMap[i]==-1 ? Double.NaN : row[colMap[i]];
      if( map[i] != null ) {    // Enum mapping
        int e = (int)d;
        if( e < 0 || e >= map[i].length ) d = Double.NaN; // User data is out of adapt range
        else {
          e = map[i][e];
          d = e==-1 ? Double.NaN : (double)e;
        }
      }
      tmp[i] = d;
    }
    return score0(tmp,preds);   // The results. */
    return null;
  }

  /** Build an adaption array.  The length is equal to the Model's vector length.
   *  Each inner 2D-array is a
   *  compressed domain map from data domains to model domains - or null for non-enum
   *  columns, or null for identity mappings.  The extra final int[] is the
   *  column mapping itself, mapping from model columns to data columns. or -1
   *  if missing.
   *  If 'exact' is true, will throw if there are:
   *    any columns in the model but not in the input set;
   *    any enums in the data that the model does not understand
   *    any enums returned by the model that the data does not have a mapping for.
   *  If 'exact' is false, these situations will use or return NA's instead.
   */
  protected int[][][] adapt( String names[], String domains[][], boolean exact) {
    int maplen = names.length;
    int map[][][] = new int[maplen][][];
    // Make sure all are compatible
    for( int c=0; c<names.length;++c) {
            // Now do domain mapping
      String ms[] = _output._domains[c];  // Model enum
      String ds[] =  domains[c];  // Data  enum
      if( ms == ds ) { // Domains trivially equal?
      } else if( ms == null ) {
        throw new IllegalArgumentException("Incompatible column: '" + _output._names[c] + "', expected (trained on) numeric, was passed a categorical");
      } else if( ds == null ) {
        if( exact )
          throw new IllegalArgumentException("Incompatible column: '" + _output._names[c] + "', expected (trained on) categorical, was passed a numeric");
        throw H2O.unimpl();     // Attempt an asEnum?
      } else if( !Arrays.deepEquals(ms, ds) ) {
        map[c] = getDomainMapping(_output._names[c], ms, ds, exact);
      } // null mapping is equal to identity mapping
    }
    return map;
  }


  /**
   * Type of missing columns during adaptation between train/test datasets
   * Overload this method for models that have sparse data handling.
   * Otherwise, NaN is used.
   * @return real-valued number (can be NaN)
   */
  protected double missingColumnsType() { return Double.NaN; }

  /** Build an adapted Frame from the given Frame. Useful for efficient bulk
   *  scoring of a new dataset to an existing model.  Same adaption as above,
   *  but expressed as a Frame instead of as an int[][]. The returned Frame
   *  does not have a response column.
   *  It returns a <b>two element array</b> containing an adapted frame and a
   *  frame which contains only vectors which where adapted (the purpose of the
   *  second frame is to delete all adapted vectors with deletion of the
   *  frame). */
  public Frame[] adapt( final Frame fr, boolean exact) {
    return adapt(fr, exact, true);
  }

  public Frame[] adapt( final Frame fr, boolean exact, boolean haveResponse) {
    Frame vfr = new Frame(fr); // To avoid modification of original frame fr
    int n = _output._names.length;
    if (haveResponse && isSupervised()) {
      int ridx = vfr.find(_output._names[_output._names.length - 1]);
      if (ridx != -1 && ridx != vfr._names.length - 1) { // Unify frame - put response to the end
        String name = vfr._names[ridx];
        vfr.add(name, vfr.remove(ridx));
      }
      n = ridx == -1 ? _output._names.length - 1 : _output._names.length;
    }
    String [] names = isSupervised() ? Arrays.copyOf(_output._names, n) : _output._names.clone();
    Frame  [] subVfr;
    // replace missing columns with NaNs (or 0s for DeepLearning with sparse data)
    subVfr = vfr.subframe(names, missingColumnsType());
    vfr = subVfr[0]; // extract only subframe but keep the rest for delete later
    Vec[] frvecs = vfr.vecs();
    boolean[] toEnum = new boolean[frvecs.length];
    if(!exact) for(int i = 0; i < n;++i)
      if(_output._domains[i] != null && !frvecs[i].isEnum()) {// if model expects domain but input frame does not have domain => switch vector to enum
        frvecs[i] = frvecs[i].toEnum();
        toEnum[i] = true;
      }
    int[][][] map = adapt(names,vfr.domains(),exact);
    assert map.length == names.length; // Be sure that adapt call above do not skip any column
    ArrayList<Vec> avecs = new ArrayList<>(); // adapted vectors
    ArrayList<String> anames = new ArrayList<>(); // names for adapted vector

    for( int c=0; c<map.length; c++ ) // Iterate over columns
      if(map[c] != null) { // Column needs adaptation
        Vec adaptedVec;
        if (toEnum[c]) { // Vector was flipped to column already, compose transformation
          adaptedVec = TransfVec.compose( (TransfVec) frvecs[c], map[c], vfr.domains()[c], false);
        } else adaptedVec = frvecs[c].makeTransf(map[c], vfr.domains()[c]);
        avecs.add(frvecs[c] = adaptedVec);
        anames.add(names[c]); // Collect right names
      } else if (toEnum[c]) { // Vector was transformed to enum domain, but does not need adaptation we need to record it
        avecs.add(frvecs[c]);
        anames.add(names[c]);
      }
    // Fill trash bin by vectors which need to be deleted later by the caller.
    Frame vecTrash = new Frame(anames.toArray(new String[anames.size()]), avecs.toArray(new Vec[avecs.size()]));
    if (subVfr[1]!=null) vecTrash.add(subVfr[1], true);
    return new Frame[] { new Frame(names,frvecs), vecTrash };
  }

  /** Returns a mapping between values of model domains (<code>modelDom</code>) and given column domain.
   *  @see #getDomainMapping(String, String[], String[], boolean) */
  public static int[][] getDomainMapping(String[] modelDom, String[] colDom, boolean exact) {
    return getDomainMapping(null, modelDom, colDom, exact);
  }

  /**
   * Returns a mapping for given column according to given <code>modelDom</code>.
   * In this case, <code>modelDom</code> is
   *
   * @param colName name of column which is mapped, can be null.
   * @param modelDom
   * @param logNonExactMapping
   * @return
   */
  public static int[][] getDomainMapping(String colName, String[] modelDom, String[] colDom, boolean logNonExactMapping) {
    int emap[] = new int[modelDom.length];
    boolean bmap[] = new boolean[modelDom.length];
    HashMap<String,Integer> md = new HashMap<>((int) ((colDom.length/0.75f)+1));
    for( int i = 0; i < colDom.length; i++) md.put(colDom[i], i);
    for( int i = 0; i < modelDom.length; i++) {
      Integer I = md.get(modelDom[i]);
      if (I == null && logNonExactMapping)
        Log.warn("Domain mapping: target domain contains the factor '"+modelDom[i]+"' which DOES NOT appear in input domain " + (colName!=null?"(column: " + colName+")":""));
      if (I!=null) {
        emap[i] = I;
        bmap[i] = true;
      }
    }
    if (logNonExactMapping) { // Inform about additional values in column domain which do not appear in model domain
      for (int i=0; i<colDom.length; i++) {
        boolean found = false;
        for (int anEmap : emap)
          if (anEmap == i) {
            found = true;
            break;
          }
        if (!found)
          Log.warn("Domain mapping: target domain DOES NOT contain the factor '"+colDom[i]+"' which appears in input domain "+ (colName!=null?"(column: " + colName+")":""));
      }
    }

    // produce packed values
    int[][] res = water.fvec.TransfVec.pack(emap, bmap);
    // Sort values in numeric order to support binary search in TransfVec
    water.fvec.TransfVec.sortWith(res[0], res[1]);
    return res;
  }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  abstract protected float[] score0( Chunk chks[], int row_in_chunk, double[] tmp, float[] preds );

  /** Subclasses implement the scoring logic.  The data is pre-loaded into a
   *  re-used temp array, in the order the model expects.  The predictions are
   *  loaded into the re-used temp array, which is also returned.  */
  protected abstract float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]);
  // Version where the user has just ponied-up an array of data to be scored.
  // Data must be in proper order.  Handy for JUnit tests.
  public double score(double [] data){ return ArrayUtils.maxIndex(score0(data, new float[_output.nclasses()]));  }
}
