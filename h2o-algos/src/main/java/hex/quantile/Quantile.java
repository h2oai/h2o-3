package hex.quantile;

import hex.Model;
import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.QuantileV2;
import water.H2O.H2OCountedCompleter;
import water.MRTask;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;

/**
 *  Quantile model builder... building a simple QuantileModel
 */
public class Quantile extends ModelBuilder<QuantileModel,QuantileModel.QuantileParameters,QuantileModel.QuantileOutput> {

  // Called from Nano thread; start the Quantile Job on a F/J thread
  public Quantile( QuantileModel.QuantileParameters parms ) { super("Quantile",parms); init(false); }

  public ModelBuilderSchema schema() { return new QuantileV2(); }

  @Override public Quantile trainModel() {
    return (Quantile)start(new QuantileDriver(), train().numCols()*_parms._probs.length);
  }

  @Override public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{Model.ModelCategory.Unknown};
  }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the probs.
   */
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
        _parms.read_lock_frames(Quantile.this); // Fetch & read-lock source frame
        init(true);

        // The model to be built
        model = new QuantileModel(dest(), _parms, new QuantileModel.QuantileOutput(Quantile.this));
        model._output._quantiles = new double[train().numCols()][_parms._probs.length];
        model.delete_and_lock(_key);

        // ---
        // Run the main Quantile Loop
        Vec vecs[] = train().vecs();
        for( int n=0; n<vecs.length; n++ ) {
          if( !isRunning() ) return; // Stopped/cancelled
          Vec vec = vecs[n];
          // Compute top-level histogram
          Histo h1 = new Histo(vec.min(),vec.max(),0,vec.length(),vec.isInt()).doAll(vec);

          // For each probability, see if we have it exactly - or else run
          // passes until we do.
          for( int p = 0; p < _parms._probs.length; p++ ) {
            double prob = _parms._probs[p];
            Histo h = h1;  // Start from the first global histogram

            while( Double.isNaN(model._output._quantiles[n][p] = h.findQuantile(prob)) )
              h = h.refinePass(prob).doAll(vec); // Full pass at higher resolution

            // Update the model
            model._output._iterations++; // One iter per-prob-per-column
            model.update(_key); // Update model in K/V store
            update(1);          // One unit of work
          }
          StringBuilder sb = new StringBuilder();
          sb.append("Quantile: iter: ").append(model._output._iterations).append(" Qs=").append(Arrays.toString(model._output._quantiles[n]));
          Log.info(sb);
        }

      } catch( Throwable t ) {
        t.printStackTrace();
        cancel2(t);
        throw t;
      } finally {
        if( model != null ) model.unlock(_key);
        _parms.read_unlock_frames(Quantile.this);
        Scope.exit(model == null ? null : model._key);
        done();                 // Job done!
      }
      tryComplete();
    }
  }

  // -------------------------------------------------------------------------

  private static class Histo extends MRTask<Histo> {
    private static final int NBINS=1024; // Default bin count
    private final int _nbins;            // Actual  bin count
    private final double _lb;            // Lower bound of bin[0]
    private final double _step;          // Step-size per-bin
    private final long _start_row;       // Starting row number for this lower-bound
    private final long _nrows;           // Total datasets rows
    private final boolean _isInt;        // Column only holds ints

    // Big Data output result
    long   _bins [/*nbins*/]; // Rows in each bin
    double _elems[/*nbins*/]; // Unique element, or NaN if not unique

    private Histo( double lb, double ub, long start_row, long nrows, boolean isInt  ) {
      _nbins = NBINS;
      _lb = lb;
      _step = (ub-lb)/_nbins;
      _start_row = start_row;
      _nrows = nrows;
      _isInt = isInt;
    }

    @Override public void map( Chunk chk ) {
      long   bins [] = _bins =new long  [_nbins];
      double elems[] = _elems=new double[_nbins];
      for( int row=0; row<chk._len; row++ ) {
        double d = chk.atd(row);
        double idx = (d-_lb)/_step;
        if( !(0.0 <= idx && idx < bins.length) ) continue;
        int i = (int)idx;
        if( bins[i]==0 ) elems[i] = d; // Capture unique value
        else if( !Double.isNaN(elems[i]) && elems[i]!=d )
          elems[i] = Double.NaN; // Not unique
        bins[i]++;               // Bump row counts
      }
    }
    @Override public void reduce( Histo h ) {
      for( int i=0; i<_nbins; i++ ) // Keep unique elements
        if( _bins[i]== 0 ) _elems[i] = h._elems[i]; // Left had none, so keep right unique
        else if( h._bins[i] > 0 && _elems[i] != h._elems[i] )
          _elems[i] = Double.NaN; // Left & right both had elements, but not equal
      ArrayUtils.add(_bins,h._bins);
    }


    /** @return Quantile for probability prob, or NaN if another pass is needed. */
    double findQuantile( double prob ) {
      double p2 = prob*(_nrows-1); // Desired fractional row number for this probability
      long r2 = (long)p2;       // Lower integral row number
      int loidx = findBin(r2);  // Find bin holding low value
      double lo = (loidx == _nbins) ? binEdge(_nbins) : _elems[loidx];
      if( Double.isNaN(lo) ) return Double.NaN; // Needs another pass to refine lo
      if( r2==p2 ) return lo;   // Exact row number?  Then quantile is exact

      long r3 = r2+1;           // Upper integral row number
      int hiidx = findBin(r3);  // Find bin holding high value
      double hi = (hiidx == _nbins) ? binEdge(_nbins) : _elems[hiidx];
      if( Double.isNaN(hi) ) return Double.NaN; // Needs another pass to refine hi
      return computeQuantile(lo,hi,r2,_nrows,prob);
    }

    private double binEdge( int idx ) { return _lb+_step*idx; }

    // bin for row; can be _nbins if just off the end (normally expect 0 to nbins-1)
    private int findBin( long row ) {
      long sum = _start_row;
      for( int i=0; i<_nbins; i++ )
        if( row < (sum += _bins[i]) )
          return i;
      return _nbins;
    }

    // Run another pass over the data, with refined endpoints, to home in on
    // the exact elements for this probability.
    Histo refinePass( double prob ) {
      double prow = prob*(_nrows-1); // Desired fractional row number for this probability
      long lorow = (long)prow;       // Lower integral row number
      int loidx = findBin(lorow);    // Find bin holding low value
      // If loidx is the last bin, then high must be also the last bin - and we
      // have an exact quantile (equal to the high bin) and we didn't need
      // another refinement pass
      assert loidx < _nbins;
      double lo = binEdge(loidx); // Lower end of range to explore
      // If probability does not hit an exact row, we need the elements on
      // either side - so the next row up from the low row
      long hirow = lorow==prow ? lorow : lorow+1;
      int hiidx = findBin(hirow);    // Find bin holding high value
      // Upper end of range to explore - except at the very high end cap
      double hi = hiidx==_nbins ? binEdge(_nbins) : binEdge(hiidx+1);

      long sum = _start_row;
      for( int i=0; i<loidx; i++ )
        sum += _bins[i];
      return new Histo(lo,hi,sum,_nrows,_isInt);
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
    double plo = (double)(row+0)/(nrows-1); // Note that row numbers are inclusive on the end point, means we need a -1
    double phi = (double)(row+1)/(nrows-1); // Passed in the row number for the low value, high is the next row, so +1
    assert plo <= prob && prob < phi;
    return lo + (hi-lo)*(prob-plo)/(phi-plo); // Classic linear interpolation
  }

}
