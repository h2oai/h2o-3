package hex;

import java.util.HashMap;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.Frame;
import water.nbhm.NonBlockingHashMap;
import water.rapids.ASTddply.Group;
import water.util.ArrayUtils;
import water.util.ReflectionUtils;

/** A Grid of Models
 *  Used to explore Model hyper-parameter space.  Lazily filled in, this object
 *  represents the potentially infinite variety of hyperparameters of a given
 *  model & dataset.
 *
 *  One subclass per kind of Model, e.g. KMeans or GLM or GBM or DL.  The Grid
 *  tracks Models and their hyperparameters, and will allow discovery of
 *  existing Models by hyperparameter, or building Models on demand by
 *  hyperparameter.  The Grid can manage a (simplistic) hyperparameter search
 *  space.
 *
 *  The external Grid API uses a HashMap<String,Object> to describe a set of
 *  hyperparameter values, where the String is a valid field name in the
 *  corresponding Model.Parameter, and the Object is the field value (boxed as
 *  needed).
 *
 *  The Grid implementation treats all hyperparameters as double values
 *  internally, indexed by a simple number.  A complete set of hyper parameters
 *  is thus a {@code double[]}, and a set of search parameters a {@code
 *  double[][]}.  The subclasses of Grid will need to convert between 
 *  these two formats.  
 *
 *  E.g. KMeansGrid will convert the initial center selection field "_init"
 *  Enum to and from a simple double value internally.
 */
public abstract class Grid<G extends Grid<G>> extends Lockable<G> {
  protected final Frame _fr;    // The training frame for this grid of models
  // A cache of double[] hyper-parameters mapping to Models
  final NonBlockingHashMap<Group,Model> _cache = new NonBlockingHashMap<>();

  protected Grid( Key key, Frame fr ) { super(key); _fr = fr; }

  /** @return Model name */
  protected abstract String modelName();

  /** @return hyperparameter names corresponding to a Model.Parameter field names */
  protected abstract String[] hyperNames();

  /** @return hyperparameter defaults, aligned with the field names */
  protected abstract double[] hyperDefaults();

  /** Ask the Grid for a suggested next hyperparameter value, given an existing
   *  Model as a starting point and the complete set of hyperparameter limits.
   *  Returning a NaN signals there is no next suggestion, which is reasonable
   *  if the obvious "next" value does not exist (e.g. exhausted all
   *  possibilities of an enum).  It is OK if a Model for the suggested value
   *  already exists; this will be checked before building any model.
   *  @param h The h-th hyperparameter 
   *  @param m A model to act as a starting point 
   *  @param hyperLimits Upper bounds for this search 
   *  @return Suggested next value for hyperparameter h or NaN if no next value */
  protected abstract double suggestedNextHyperValue( int h, Model m, double[] hyperLimits );

  /** @return The data frame used to train all these models.  All models are
   *  trained on the same data frame, but might be validated on multiple
   *  different frames. */
  public Frame trainingFrame() { return _fr; }

  /** @return Factory method to return the grid for a particular modeling class
   *  and frame.  */
  protected static Key keyName( String modelName, Frame fr ) {
    if( fr._key==null ) throw new IllegalArgumentException("The frame being grid-searched over must have a Key");
    return Key.make("Grid_"+modelName+"_"+fr._key.toString());
  }

  /** Convert a collection of hyper-parameter search arrays into a double-
   *  dimension array-of-doubles.  Missing hyper parms will be filled in with
   *  the default value.
   *  @param hypers A set of {hyper-parameter field names, search space values}
   *  @return The same set as a double[][]  */
  private double[][] hyper2doubles( HashMap<String,Object[]> hypers ) {
    String[] ss = hyperNames();
    double[] defs = hyperDefaults();
    double[][] dss = new double[ss.length][];
    int cnt=0;                         // Count of found hyper parameters
    for( int i=0; i<ss.length; i++ ) { // For all hyper-names
      Object[] os = hypers.get(ss[i]); // Get an array-of-something
      if( os == null ) os = new Object[]{defs[i]}; // Missing?  Use default
      else cnt++;                                  // Found a hyper parameter
      double[] ds = dss[i] = new double[os.length];// Array of params for search
      for( int j=0; j<os.length; j++ )
        ds[j] = ReflectionUtils.asDouble(os[j]);
    }
    if( cnt != hypers.size() )  // Quicky error check for unknow parms
      for( String s : hypers.keySet() )
        if( ArrayUtils.find(ss, s) == -1 )
          throw new IllegalArgumentException("Unkown hyper-parameter "+s);
    return dss;
  }

  /** Convert a collection of hyper-parameters into an array-of-doubles.
   *  Missing hyper parms will be filled in with the default value.
   *  Error if the value cannot be represented as a double.
   *  @param hypers A set of {hyper-parameter field names, values}
   *  @return The same set as a double[]  */
  private double[] hyper2double( HashMap<String,Object> hypers ) {
    throw H2O.unimpl();
  }

  /** @param hypers A set of hyper parameter values
   *  @return A model run with these parameters, or null if the model does not exist. */
  public Model model( double[] hypers ) { return _cache.get(new Group(hypers)); }
  public Model model( HashMap<String,Object> hypers ) { return model(hyper2double(hypers)); }

  /** @param hypers A set of hyper parameter values
   *  @return A ModelBuilder, blindly filled with parameters.  Assumed to be
   *  cheap; used to check hyperparameter sanity */
  protected abstract ModelBuilder getBuilder( double[] hypers );

  /** @param parms Model parameters
   *  @return Gridable parameters pulled out of the parms */
  public abstract double[] getHypers( Model.Parameters parms );

  /** @param hypers A set of hyper parameter values
   *  @return A Future of a model run with these parameters, typically built on
   *  demand and not cached - expected to be an expensive operation.  If the
   *  model in question is "in progress", a 2nd build will NOT be kicked off.
   *  This is a non-blocking call. */
  private ModelBuilder startBuildModel( double[] hypers ) {
    if( model(hypers) != null ) return null;
    ModelBuilder mb = getBuilder(hypers);
    mb.trainModel();
    return mb;
  }
  
  /** @param hypers A set of hyper parameter values
   *  @return A model run with these parameters, typically built on demand and
   *  cached - expected to be an expensive operation.  If the model in question
   *  is "in progress", a 2nd build will NOT be kicked off.  This is a blocking call. */
  private Model buildModel( double[] hypers ) {
    Model m = model(hypers);
    if( m != null ) return m;
    m = (Model)(startBuildModel(hypers).get());
    _cache.put(new Group(hypers.clone()), m);
    return m;
  }
  
  /** @param hyperSearch A set of arrays of hyper parameter values, used to
   *  specify a simple fully-filled-in grid search.
   *  @return GridSearch Job, with models run with these parameters, built as
   *  needed - expected to be an expensive operation.  If the models in
   *  question are "in progress", a 2nd build will NOT be kicked off.  This is
   *  a non-blocking call. */
  public GridSearch startGridSearch( final HashMap<String,Object[]> hyperSearch ) { return new GridSearch(_key,hyperSearch).start(); }

  // Cleanup models and grid
  @Override protected Futures remove_impl( Futures fs ) {
    for( Model m : _cache.values() )
      m.remove(fs);
    _cache.clear();
    return fs;
  }

  // A search over a hyperparameter space
  public class GridSearch extends Job<Grid> {
    double[][] _hyperSearch;
    final int _total_models;
    GridSearch( Key gkey, HashMap<String,Object[]> hyperSearch ) {
      super(Key.make("GridSearch_" + modelName() + Key.rand()), gkey, modelName() + " Grid Search");
      _hyperSearch = hyper2doubles(hyperSearch);

      // Count of models in this search
      int work = 1;
      for( double hparms[] : _hyperSearch )
        work *= hparms.length;
      _total_models = work;

      // Check all parameter combos for validity
      double[] hypers = new double[_hyperSearch.length];
      for( int[] hidx = new int[_hyperSearch.length]; hidx != null; hidx = nextModel(hidx) ) {
        ModelBuilder mb = getBuilder(hypers(hidx,hypers));
        mb.init(false);
        if( mb.error_count() > 0 ) 
          throw new IllegalArgumentException(mb.validationErrors());
      }
    }

    GridSearch start() {
      start(new H2OCountedCompleter() { @Override public void compute2() { gridSearch(); tryComplete(); } },_total_models);
      return this;
    }

    /** @return the set of models covered by this grid search, some may be null
     *  if the search is in progress or otherwise incomplete. */
    public Model[] models() {
      Model[] ms = new Model[_total_models];
      int mcnt = 0;
      double[] hypers = new double[_hyperSearch.length];
      for( int[] hidx = new int[_hyperSearch.length]; hidx != null; hidx = nextModel(hidx) )
        ms[mcnt++] = model(hypers(hidx,hypers));
      return ms;
    }

    // Classic grid search over hyper-parameter space
    private void gridSearch() {
      double[] hypers = new double[_hyperSearch.length];
      for( int[] hidx = new int[_hyperSearch.length]; hidx != null; hidx = nextModel(hidx) ) {
        if( !isRunning() ) { cancel(); return; }
        buildModel(hypers(hidx,hypers));
      }
      done();
    }

    // Dumb iteration over the hyper-parameter space.
    // Return NULL at end
    private int[] nextModel( int[] hidx ) {
      // Find the next parm to flip
      int i;
      for( i=0; i<hidx.length; i++ )
        if( hidx[i]+1 < _hyperSearch[i].length )
          break;
      if( i==hidx.length ) return null; // All done, report null
      // Flip indices
      for( int j=0; j<i; j++ ) hidx[j]=0;
      hidx[i]++;
      return hidx;
    }
    private double[] hypers( int[] hidx, double[] hypers ) {
      for( int i=0; i<hidx.length; i++ )
        hypers[i] = _hyperSearch[i][hidx[i]];
      return hypers;            // Flow coding
    }
  }
}
