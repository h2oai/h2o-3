package hex.kmeans2;

import hex.ModelBuilder;
import hex.schemas.KMeans2V2;
import hex.schemas.ModelBuilderSchema;
import water.H2O.H2OCountedCompleter;
import water.MRTask;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;
import java.util.Random;

/**
 *  KMeans2 model builder... building a trivial KMeans2Model
 */
public class KMeans2 extends ModelBuilder<KMeans2Model,KMeans2Model.KMeans2Parameters,KMeans2Model.KMeans2Output> {

  // Called from Nano thread; start the KMeans2 Job on a F/J thread
  public KMeans2( KMeans2Model.KMeans2Parameters parms ) { super("KMeans2",parms); init(false); }

  public ModelBuilderSchema schema() { return new KMeans2V2(); }

  @Override public KMeans2 trainModel() {
    return (KMeans2)start(new KMeans2Driver(), _parms._max_iters);
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
    if( _parms._max_iters < 1 || _parms._max_iters > 9999999 )
      error("max_iters", "must be between 1 and 10 million");
    if( _parms._K < 2 || _parms._K > 999999 )
      error("K","must be between 2 and a million");
  }

  // ----------------------
  private class KMeans2Driver extends H2OCountedCompleter<KMeans2Driver> {

    @Override protected void compute2() {
      KMeans2Model model = null;
      try {
        Scope.enter();
        _parms.lock_frames(KMeans2.this); // Fetch & read-lock source frame
        init(true);

        // The model to be built
        model = new KMeans2Model(dest(), _parms, new KMeans2Model.KMeans2Output(KMeans2.this));
        model.delete_and_lock(_key);

        // Pseudo-random initial cluster selection
        Frame f = train();
        double clusters[/*K*/][/*N*/] = model._output._clusters = new double[_parms._K][f.numCols()];
        Random R = new Random(1234);
        for( int k=0; k<_parms._K; k++ ) {
          long row = Math.abs(R.nextLong() % f.numRows());
          for( int j=0; j<f.numCols(); j++ )
            clusters[k][j] = f.vecs()[j].at(row);
        }
        model.update(_key); // Update model in K/V store

        // ---
        // Run the main KMeans2 Loop
        // Stop after enough iterations
        double last_mse = Double.MAX_VALUE; // MSE from prior iteration
        for( ; model._output._iters < _parms._max_iters; model._output._iters++ ) {
          if( !isRunning() ) break; // Stopped/cancelled

          Lloyds ll = new Lloyds(clusters).doAll(f);
          clusters = model._output._clusters = ArrayUtils.div(ll._sums,ll._rows);
          model._output._mse = ll._se/f.numRows();

          // Fill in the model
          model.update(_key); // Update model in K/V store
          update(1);          // One unit of work

          StringBuilder sb = new StringBuilder();
          sb.append("KMeans2: iter: ").append(model._output._iters).append(" ").append(model._output._mse).append(" ROWS=").append(Arrays.toString(ll._rows));
          Log.info(sb);

          double improv = (last_mse-model._output._mse) / model._output._mse;
          if( improv < 1e-6 ) break;
          last_mse = model._output._mse;
        }

      } catch( Throwable t ) {
        t.printStackTrace();
        cancel2(t);
        throw t;
      } finally {
        if( model != null ) model.unlock(_key);
        _parms.unlock_frames(KMeans2.this);
        Scope.exit(model._key);
        done();                 // Job done!
      }
      tryComplete();
    }

    class Lloyds extends MRTask<Lloyds> {
      private final double[/*K*/][/*N*/] _clusters; // Old cluster
      private double[/*K*/][/*N*/] _sums; // Sum of points in new cluster
      private long[/*K*/] _rows;          // Number of points in new cluster
      private double _se;                 // Squared Error
      Lloyds( double clusters[][] ) { _clusters = clusters; }
      @Override public void map( Chunk[] chks ) {
        double[] ds = new double[chks.length];
        _sums = new double[_parms._K][chks.length];
        _rows = new long  [_parms._K];
        for( int row=0; row<chks[0]._len; row++ ) {
          for( int i=0; i<ds.length; i++ ) ds[i] = chks[i].at0(row);
          // Find distance to cluster 0
          int nearest=0;
          double dist = distance(ds,_clusters[nearest]);
          // Find nearest cluster, and its distance
          for( int k=1; k<_parms._K; k++ ) {
            double dist2 = distance(ds,_clusters[k]);
            if( dist2 < dist ) { dist = dist2; nearest = k; }
          }
          // Add the point into the chosen cluster
          ArrayUtils.add(_sums[nearest],ds);
          _rows[nearest]++;
          // Accumulate squared-error (which is just squared-distance)
          _se += dist;
        }
      }
      @Override public void reduce( Lloyds ll ) {
        ArrayUtils.add(_sums,ll._sums);
        ArrayUtils.add(_rows,ll._rows);
        _se += ll._se;
      }
    }
    
  }

  static double distance( double[] ds0, double[] ds1 ) {
    double sum=0;
    for( int i=0; i<ds0.length; i++ )
      sum += (ds0[i]-ds1[i])*(ds0[i]-ds1[i]);
    return sum;
  }
}
