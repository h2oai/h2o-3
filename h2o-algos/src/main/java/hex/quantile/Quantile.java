package hex.quantile;

import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.QuantileV2;
import water.H2O.H2OCountedCompleter;
import water.MRTask;
import water.Scope;
import water.H2O;
import water.Iced;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;

/**
 *  Quantile model builder... building a trivial QuantileModel
 */
public class Quantile extends ModelBuilder<QuantileModel,QuantileModel.QuantileParameters,QuantileModel.QuantileOutput> {

  // Called from Nano thread; start the Quantile Job on a F/J thread
  public Quantile( QuantileModel.QuantileParameters parms ) { super("Quantile",parms); init(false); }

  public ModelBuilderSchema schema() { return new QuantileV2(); }

  @Override public Quantile trainModel() {
    return (Quantile)start(new QuantileDriver(), train().numCols());
  }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the max_iters. */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    for( double p : _parms._probs )
      if( p < 0.0 || p > 1.0 )
        error("_probs","Probabilities must be between 0 and 1");
  }

  // ----------------------
  private class QuantileDriver extends H2OCountedCompleter<QuantileDriver> {

    @Override protected void compute2() {
      QuantileModel model = null;
      try {
        Scope.enter();
        _parms.lock_frames(Quantile.this); // Fetch & read-lock source frame
        init(true);

        // The model to be built
        model = new QuantileModel(dest(), _parms, new QuantileModel.QuantileOutput(Quantile.this));
        model._output._quantiles = new double[train().numCols()][_parms._probs.length];
        model.delete_and_lock(_key);

        // ---
        // Run the main Quantile Loop
        // Stop after enough iterations
        Vec vecs[] = train().vecs();
        for( int n=0; n<vecs.length; n++ ) {
          if( !isRunning() ) return; // Stopped/cancelled


.... try again... do not optimize for multi passes, except for the 1st one....
.... each pass does 1 range of bins, no recursion, 
          // Pass over the data; radix-sort / histogram
          Histo h = new Histo(vecs[n]);
          boolean again = true; // Needs another pass?
          while( again ) {
            new DoHisto(h).doAll(vecs[n]);
            h.rollUp();
            again = false;      // Not yet...
            // find the quantile for each probability p
            for( int p = 0; p < _parms._probs.length; p++ ) {
              double q = h.findQuantile(_parms._probs[p]);
              if( Double.isNaN(q) ) // My flag for saying "go again"
                again = true;       // Needs another pass
              model._output._quantiles[n][p] = q;
            }
            
          }
          model._output._iters = n;

          // Fill in the model
          model.update(_key); // Update model in K/V store
          update(1);          // One unit of work
          StringBuilder sb = new StringBuilder();
          sb.append("Quantile: iter: ").append(model._output._iters).append(" Qs=").append(Arrays.toString(model._output._quantiles[n]));
          Log.info(sb);
        }

      } catch( Throwable t ) {
        t.printStackTrace();
        cancel2(t);
        throw t;
      } finally {
        if( model != null ) model.unlock(_key);
        _parms.unlock_frames(Quantile.this);
        Scope.exit(model._key);
        done();                 // Job done!
      }
      tryComplete();
    }
  }

  /** Compute the correct final quantile from these 4 values.  If the lo and hi
   *  elements are equal, use them.  However if they differ, then there is no
   *  single value which exactly matches the desired quantile.  There are
   *  several well-accepted definitions in this case - including picking either
   *  the lo or the hi, or averaging them, or doing a linear interpolation.
   *  @param lo  the highest element less    than or equal to the desired quantile
   *  @param hi  the lowest  element greater than or equal to the desired quantile
   *  @param row row number (zero based) of the lo element; high element is +1
   *  @return desired quantile. */
  static double computeQuantile( double lo, double hi, long row, long nrows, double prob ) {
    if( lo==hi ) return lo;     // Equal; pick either
    // Unequal, linear interpolation
    double plo = (double)(row+0)/(nrows-1);
    double phi = (double)(row+1)/(nrows-1);
    assert plo <= prob && prob < phi;
    double q = lo + (hi-lo)*(prob-plo)/(phi-plo);
    return q;
  }

  // -------------------------------------------------------------------------

  private static class DoHisto extends MRTask<DoHisto> {
    private final Histo _h;
    DoHisto( Histo h ) { _h = h; }
    @Override public void map( Chunk chk ) {
      for( int row=0; row<chk._len; row++ )  
        _h.add(chk.at0(row));
    }
    @Override public void reduce( DoHisto h ) { _h.reduce(h._h); }
  }

  private static class Histo extends Iced {
    private static final int NBINS=1024; // Default bin count
    private final int _nbins;            // Actual  bin count
    private final double _lb;            // Lower bound of bin[0]
    private final double _step;          // Step-size per-bin
    private final long _nrows;           // Total datasets rows in this histogram
    private long _cumBins[/*nbins*/];    // Cumulative row counts
    private final boolean _isInt;        // Column only holds ints

    // Big Data output result
    long   _bins [/*nbins*/]; // Rows in each bin
    double _elems[/*nbins*/]; // Unique element, or NaN if not unique

    private Histo _hs[/*nbins*/];        // Recursive histograms

    /** Top-level histogram covering an entire Vec */
    Histo( Vec vec ) { this( vec.min(), vec.max(), vec.length(), vec.isInt() ); }

    /** Refined histogram */
    Histo( Histo parent, int idx ) { this( parent.binEdge(idx), parent.binEdge(idx+1), parent._bins[idx], parent._isInt ); }

    private Histo( double lb, double ub, long nrows, boolean isInt  ) { 
      _nrows = nrows;
      _nbins = NBINS;
      _isInt = isInt;
      _lb = lb;
      _step = (ub-lb)/_nbins;
      _cumBins = new long[_nbins];
    }

    private double binEdge( int idx ) { return _lb+_step*idx; }

    void add( double d ) {
      if( _hs == null ) {       // Leaf histogram
        // Leaf histogram: build the whole thing
        long   bins [] = _bins ==null ? (_bins =new long  [_nbins]) : _bins ;
        double elems[] = _elems==null ? (_elems=new double[_nbins]) : _elems;
        double idx = (d-_lb)/_step;
        if( !(0.0 <= idx && idx < bins.length) ) return;
        int i = (int)idx;
        if( bins[i]==0 ) elems[i] = d; // Capture unique value
        else if( !Double.isNaN(elems[i]) && elems[i]!=d ) 
          elems[i] = Double.NaN; // Not unique
        bins[i]++;               // Bump row counts
        
      } else {      // Interior tree histogram; pass along to interested leaves
        throw H2O.unimpl();
      }
    }
    void reduce( Histo h ) { 
      for( int i=0; i<_nbins; i++ ) // Keep unique elements
        if( _bins[i]== 0 ) _elems[i] = h._elems[i]; // Left had none, so keep right
        else if( h._bins[i] > 0 && _elems[i] != h._elems[i] )
          _elems[i] = Double.NaN; // Left & right both had elements, but not equal
      ArrayUtils.add(_bins,h._bins);
      if( _hs != null ) throw H2O.unimpl();
    }


    Histo rollUp() {
      _cumBins[0] = _bins[0];
      for( int i=1; i<_bins.length; i++ ) {
        _cumBins[i] = _cumBins[i-1]+_bins[i];
        if( _hs != null && _hs[i] != null )
          _hs[i].rollUp();
      }
      return this;
    }
    
    /** @return Quantile for probability p, or NaN if another pass is needed. */
    double findQuantile( double p ) {
      double p2 = p*(_nrows-1); // Desired fractional row number
      long r2 = (long)p2;       // Lower integral row number
      int loidx = findBin(r2);  // Find bin holding low value
      double lo = (loidx == _nbins) ? binEdge(_nbins) : _elems[loidx];
      if( _hs != null && _hs[loidx] != null )  throw H2O.unimpl();

      long r3 = r2==p2 ? r2 : r2+1; // Upper integral row number
      int hiidx = findBin(r3);  // Find bin holding high value
      double hi = (hiidx == _nbins) ? binEdge(_nbins) : _elems[hiidx];
      if( _hs != null && _hs[hiidx] != null )  throw H2O.unimpl();
      if( Double.isNaN(lo) ) refineAt(loidx); // Needs another pass to refine lo
      if( Double.isNaN(hi) ) refineAt(hiidx); // Needs another pass to refine hi
      return computeQuantile(lo,hi,r2,_nrows,p);
    }

    // bin for row; can be _nbins if just off the end (normally expect 0 to nbins-1)
    int findBin( long row ) {
      for( int i=0; i<_nbins; i++ )
        if( row < _cumBins[i] )
          return i;
      return _nbins;
    }


    /** Add a refining Histogram layer for this bin */
    void refineAt( int idx ) {
      if( _hs == null ) _hs = new Histo[_nbins];
      if( _hs[idx] == null )
        _hs[idx] = new Histo(this,idx);
    }
  }
}
