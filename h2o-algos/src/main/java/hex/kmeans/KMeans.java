package hex.kmeans;

import hex.ModelBuilder;
import hex.schemas.KMeansV2;
import hex.schemas.ModelBuilderSchema;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.RandomUtils;

import java.util.ArrayList;
import java.util.Random;

/**
 * Scalable K-Means++ (KMeans||)<br>
 * http://theory.stanford.edu/~sergei/papers/vldb12-kmpar.pdf<br>
 * http://www.youtube.com/watch?v=cigXAxV3XcY
 */
public class KMeans extends ModelBuilder<KMeansModel,KMeansModel.KMeansParameters,KMeansModel.KMeansOutput> {
  public enum Initialization {
    None, PlusPlus, Furthest
  }

  // Number of categorical columns
  private int _ncats;

  // Number of reinitialization attempts for preventing empty clusters
  transient private int reinit_attempts;

  // Called from an http request
  public KMeans( KMeansModel.KMeansParameters parms) {
    super(Key.make("KMeansModel"),"K-means",parms);
  }

  public ModelBuilderSchema schema() { return new KMeansV2(); }


  /** Start the KMeans training Job on an F/J thread. */
  @Override public Job<KMeansModel> train() {
    return start(new KMeansDriver(), _parms._max_iters);
  }

  // ----------------------
  private class KMeansDriver extends H2OCountedCompleter<KMeansDriver> {

    @Override protected void compute2() {
      Frame fr = null;
      KMeansModel model = null;
      try {
        // Fetch & read-lock source frame
        Value val = DKV.get(_parms._src);
        if( val == null ) throw new IllegalArgumentException("Missing frame "+_parms._src);
        fr = val.get();
        fr.read_lock(_key);
        if ( fr.numRows() < _parms._K) throw new IllegalArgumentException("Cannot make " + _parms._K + " clusters out of " + fr.numRows() + " rows.");

        // Sort columns, so the categoricals are all up front.  They use a
        // different distance metric than numeric columns.
        Vec vecs[] = fr.vecs();
        final int N = vecs.length; // Feature count
        int ncats=0, len=N;
        while( ncats != len ) {
          while( ncats < len && vecs[ncats].isEnum() ) ncats++;
          while( len > 0 && !vecs[len-1].isEnum() ) len--;
          if( ncats < len-1 ) fr.swap(ncats,len-1);
        }
        _ncats = ncats;

        // The model to be built
        model = new KMeansModel(dest(), fr, _parms, new KMeansModel.KMeansOutput(), ncats);
        model.delete_and_lock(_key);

        // means are used to impute NAs
        double[] means = new double[N];
        for( int i = 0; i < N; i++ )
          means[i] = vecs[i].mean();
        // mults & means for normalization
        double[] mults = null;
        if( _parms._normalize ) {
          mults = new double[N];
          for( int i = 0; i < N; i++ ) {
            double sigma = vecs[i].sigma();
            mults[i] = normalize(sigma) ? 1.0 / sigma : 1.0;
          }
        }

        // Initialize clusters
        Random rand = water.util.RandomUtils.getRNG(_parms._seed - 1);
        double clusters[][];    // Normalized cluster centers
        if( _parms._init == Initialization.None ) {
          // Initialize all clusters to random rows
          clusters = model._output._clusters = new double[_parms._K][fr.numCols()];
          for( double[] cluster : clusters )
            randomRow(vecs, rand, cluster, means, mults);
        } else {
          clusters = new double[1][vecs.length];
          // Initialize first cluster to random row
          randomRow(vecs, rand, clusters[0], means, mults);

          while( model._output._iters < 5 ) {
            // Sum squares distances to clusters
            SumSqr sqr = new SumSqr(clusters,means,mults,_ncats).doAll(vecs);

            // Sample with probability inverse to square distance
            Sampler sampler = new Sampler(clusters, means, mults, _ncats, sqr._sqr, _parms._K * 3, _parms._seed).doAll(vecs);
            clusters = ArrayUtils.append(clusters,sampler._sampled);

            // Fill in sample clusters into the model
            if( !isRunning() ) return; // Stopped/cancelled
            model._output._clusters = denormalize(clusters, ncats, means, mults);
            model._output._mse = sqr._sqr/fr.numRows();

            model._output._iters++;     // One iteration done

            // This doesn't count towards model building (we didn't account these iterations as work to be done during construction)
            // update(1);          // One unit of work

            model.update(_key); // Early version of model is visible
          }
          // Recluster down to K normalized clusters
          clusters = recluster(clusters, rand);
        }
        model._output._iters = 0;     // Reset iteration count

        // ---
        // Run the main KMeans Clustering loop
        // Stop after enough iterations
        LOOP:
        for( ; model._output._iters < _parms._max_iters; model._output._iters++ ) {
          if( !isRunning() ) return; // Stopped/cancelled
          Lloyds task = new Lloyds(clusters,means,mults,_ncats, _parms._K).doAll(vecs);
          // Pick the max categorical level for clusters' center
          max_cats(task._cMeans,task._cats);

          // Handle the case where some clusters go dry.  Rescue only 1 cluster
          // per iteration ('cause we only tracked the 1 worst row)
          boolean badrow=false;
          for( int clu=0; clu<_parms._K; clu++ ) {
            if (task._rows[clu] == 0) {
              // If we see 2 or more bad rows, just re-run Lloyds to get the
              // next-worst row.  We don't count this as an iteration, because
              // we're not really adjusting the centers, we're trying to get
              // some centers *at-all*.
              if (badrow) {
                Log.warn("KMeans: Re-running Lloyds to re-init another cluster");
                model._output._iters--; // Do not count against iterations
                if (reinit_attempts++ < _parms._K) {
                  continue LOOP;  // Rerun Lloyds, and assign points to centroids
                } else {
                  reinit_attempts = 0;
                  break; //give up and accept empty cluster
                }
              }
              long row = task._worst_row;
              Log.warn("KMeans: Re-initializing cluster " + clu + " to row " + row);
              data(clusters[clu] = task._cMeans[clu], vecs, row, means, mults);
              task._rows[clu] = 1;
              badrow = true;
            }
          }

          // Fill in the model; denormalized centers
          model._output._clusters = denormalize(task._cMeans, ncats, means, mults);
          model._output._rows = task._rows;
          model._output._mses = task._cSqr;
          double ssq = 0;       // sum squared error
          for( int i=0; i<_parms._K; i++ ) {
            ssq += model._output._mses[i]; // sum squared error all clusters
            model._output._mses[i] /= task._rows[i]; // mse per-cluster
          }
          model._output._mse = ssq/fr.numRows(); // mse total
          model.update(_key); // Update model in K/V store
          update(1);          // One unit of work

          // Compute change in clusters centers
          double sum=0;
          for( int clu=0; clu<_parms._K; clu++ )
            sum += distance(clusters[clu],task._cMeans[clu],ncats);
          sum /= N;             // Average change per feature
          Log.info("KMeans: Change in cluster centers="+sum);
          if( sum < 1e-6 ) break;  // Model appears to be stable
          clusters = task._cMeans; // Update cluster centers

          StringBuilder sb = new StringBuilder();
          sb.append("KMeans: iter: ").append(model._output._iters).append(", MSE=").append(model._output._mse);
          for( int i=0; i<_parms._K; i++ )
            sb.append(", ").append(task._cSqr[i]).append("/").append(task._rows[i]);
          Log.info(sb);
        }

      } catch( Throwable t ) {
        t.printStackTrace();
        cancel2(t);
        throw t;
      } finally {
        if( model != null ) model.unlock(_key);
        if( fr != null ) fr.unlock(_key);
        done();                 // Job done!
      }
      tryComplete();
    }
  }

  // -------------------------------------------------------------------------
  // Initial sum-of-square-distance to nearest cluster
  private static class SumSqr extends MRTask<SumSqr> {
    // IN
    double[][] _clusters;
    double[] _means, _mults; // Normalization
    final int _ncats;

    // OUT
    double _sqr;

    SumSqr( double[][] clusters, double[] means, double[] mults, int ncats ) {
      _clusters = clusters;
      _means = means;
      _mults = mults;
      _ncats = ncats;
    }

    @Override public void map(Chunk[] cs) {
      double[] values = new double[cs.length];
      ClusterDist cd = new ClusterDist();
      for( int row = 0; row < cs[0].len(); row++ ) {
        data(values, cs, row, _means, _mults);
        _sqr += minSqr(_clusters, values, _ncats, cd);
      }
      _means = _mults = null;
      _clusters = null;
    }

    @Override public void reduce(SumSqr other) { _sqr += other._sqr; }
  }

  // -------------------------------------------------------------------------
  // Sample rows with increasing probability the farther they are from any
  // cluster.
  private static class Sampler extends MRTask<Sampler> {
    // IN
    double[][] _clusters;
    double[] _means, _mults; // Normalization
    final int _ncats;
    final double _sqr;           // Min-square-error
    final double _probability;   // Odds to select this point
    final long _seed;

    // OUT
    double[][] _sampled;   // New clusters

    Sampler( double[][] clusters, double[] means, double[] mults, int ncats, double sqr, double prob, long seed ) {
      _clusters = clusters;
      _means = means;
      _mults = mults;
      _ncats = ncats;
      _sqr = sqr;
      _probability = prob;
      _seed = seed;
    }

    @Override public void map(Chunk[] cs) {
      double[] values = new double[cs.length];
      ArrayList<double[]> list = new ArrayList<>();
      Random rand = RandomUtils.getRNG(_seed + cs[0].start());
      ClusterDist cd = new ClusterDist();

      for( int row = 0; row < cs[0].len(); row++ ) {
        data(values, cs, row, _means, _mults);
        double sqr = minSqr(_clusters, values, _ncats, cd);
        if( _probability * sqr > rand.nextDouble() * _sqr )
          list.add(values.clone());
      }

      _sampled = new double[list.size()][];
      list.toArray(_sampled);
      _clusters = null;
      _means = _mults = null;
    }

    @Override public void reduce(Sampler other) {
      _sampled = ArrayUtils.append(_sampled, other._sampled);
    }
  }

  // ---------------------------------------
  // A Lloyd's pass:
  //   Find nearest cluster for every point;
  //   Compute new mean/center & variance & rows for each cluster;
  //   Compute distance between clusters
  //   Compute total sqr distance

  private static class Lloyds extends MRTask<Lloyds> {
    // IN
    double[][] _clusters;
    double[] _means, _mults;      // Normalization
    final int _ncats, _K;

    // OUT
    double[][] _cMeans;         // Means for each cluster
    long[/*K*/][/*ncats*/][] _cats; // Histogram of cat levels
    double[] _cSqr;             // Sum of squares for each cluster
    long[] _rows;               // Rows per cluster
    long _worst_row;            // Row with max err
    double _worst_err;          // Max-err-row's max-err

    Lloyds( double[][] clusters, double[] means, double[] mults, int ncats, int K ) {
      _clusters = clusters;
      _means = means;
      _mults = mults;
      _ncats = ncats;
      _K = K;
    }

    @Override public void map(Chunk[] cs) {
      int N = cs.length;
      assert _clusters[0].length==N;
      _cMeans = new double[_K][N];
      _cSqr = new double[_K];
      _rows = new long[_K];
      // Space for cat histograms
      _cats = new long[_K][_ncats][];
      for( int clu=0; clu<_K; clu++ )
        for( int col=0; col<_ncats; col++ )
          _cats[clu][col] = new long[cs[col].vec().cardinality()];
      _worst_err = 0;

      // Find closest cluster for each row
      double[] values = new double[N];
      ClusterDist cd = new ClusterDist();
      for( int row = 0; row < cs[0].len(); row++ ) {
        data(values, cs, row, _means, _mults);
        closest(_clusters, values, _ncats, cd);
        int clu = cd._cluster;
        assert clu != -1; // No broken rows
        _cSqr[clu] += cd._dist;

        // Add values and increment counter for chosen cluster
        for( int col = 0; col < _ncats; col++ )
          _cats[clu][col][(int)values[col]]++; // Histogram the cats
        for( int col = _ncats; col < N; col++ )
          _cMeans[clu][col] += values[col];
        _rows[clu]++;
        // Track worst row
        if( cd._dist > _worst_err) { _worst_err = cd._dist; _worst_row = cs[0].start()+row; }
      }
      // Scale back down to local mean
      for( int clu = 0; clu < _K; clu++ )
        if( _rows[clu] != 0 ) ArrayUtils.div(_cMeans[clu],_rows[clu]);
      _clusters = null;
      _means = _mults = null;
    }

    @Override public void reduce(Lloyds mr) {
      for( int clu = 0; clu < _K; clu++ ) {
        long ra =    _rows[clu];
        long rb = mr._rows[clu];
        double[] ma =    _cMeans[clu];
        double[] mb = mr._cMeans[clu];
        for( int c = 0; c < ma.length; c++ ) // Recursive mean
          if( ra+rb > 0 ) ma[c] = (ma[c] * ra + mb[c] * rb) / (ra + rb);
      }
      ArrayUtils.add(_cats, mr._cats);
      ArrayUtils.add(_cSqr, mr._cSqr);
      ArrayUtils.add(_rows, mr._rows);
      // track global worst-row
      if( _worst_err < mr._worst_err) { _worst_err = mr._worst_err; _worst_row = mr._worst_row; }
    }
  }

  // A pair result: nearest cluster, and the square distance
  private static final class ClusterDist { int _cluster; double _dist;  }

  private static double minSqr(double[][] clusters, double[] point, int ncats, ClusterDist cd) {
    return closest(clusters, point, ncats, cd, clusters.length)._dist;
  }

  private static double minSqr(double[][] clusters, double[] point, int ncats, ClusterDist cd, int count) {
    return closest(clusters,point,ncats,cd,count)._dist;
  }

  private static ClusterDist closest(double[][] clusters, double[] point, int ncats, ClusterDist cd) {
    return closest(clusters, point, ncats, cd, clusters.length);
  }

  private static double distance(double[] cluster, double[] point, int ncats) {
    double sqr = 0;             // Sum of dimensional distances
    int pts = point.length;     // Count of valid points

    // Categorical columns first.  Only equals/unequals matters (i.e., distance is either 0 or 1).
    for(int column = 0; column < ncats; column++) {
        double d = point[column];
      if( Double.isNaN(d) ) pts--;
      else if( d != cluster[column] )
        sqr += 1.0;           // Manhatten distance
    }
    // Numeric column distance
    for( int column = ncats; column < cluster.length; column++ ) {
      double d = point[column];
      if( Double.isNaN(d) ) pts--; // Do not count
      else {
        double delta = d - cluster[column];
        sqr += delta * delta;
      }
    }
    // Scale distance by ratio of valid dimensions to all dimensions - since
    // we did not add any error term for the missing point, the sum of errors
    // is small - ratio up "as if" the missing error term is equal to the
    // average of other error terms.  Same math another way:
    //   double avg_dist = sqr / pts; // average distance per feature/column/dimension
    //   sqr = sqr * point.length;    // Total dist is average*#dimensions
    if( 0 < pts && pts < point.length )
      sqr *= point.length / pts;
    return sqr;
  }

  /** Return both nearest of N cluster/centroids, and the square-distance. */
  private static ClusterDist closest(double[][] clusters, double[] point, int ncats, ClusterDist cd, int count) {
    int min = -1;
    double minSqr = Double.MAX_VALUE;
    for( int cluster = 0; cluster < count; cluster++ ) {
      double sqr = distance(clusters[cluster],point,ncats);
      if( sqr < minSqr ) {      // Record nearest cluster
        min = cluster;
        minSqr = sqr;
      }
    }
    cd._cluster = min;          // Record nearest cluster
    cd._dist = minSqr;          // Record square-distance
    return cd;                  // Return for flow-coding
  }

  // For KMeansModel scoring; just the closest cluster
  static int closest(double[][] clusters, double[] point, int ncats) {
    int min = -1;
    double minSqr = Double.MAX_VALUE;
    for( int cluster = 0; cluster < clusters.length; cluster++ ) {
      double sqr = distance(clusters[cluster],point,ncats);
      if( sqr < minSqr ) {      // Record nearest cluster
        min = cluster;
        minSqr = sqr;
      }
    }
    return min;
  }

  // KMeans++ re-clustering
  private double[][] recluster(double[][] points, Random rand) {
    double[][] res = new double[_parms._K][];
    res[0] = points[0];
    int count = 1;
    ClusterDist cd = new ClusterDist();
    switch( _parms._init ) {
    case None:
      break;
    case PlusPlus: { // k-means++
      while( count < res.length ) {
        double sum = 0;
        for (double[] point1 : points) sum += minSqr(res, point1, _ncats, cd, count);

        for (double[] point : points) {
          if (minSqr(res, point, _ncats, cd, count) >= rand.nextDouble() * sum) {
            res[count++] = point;
            break;
          }
        }
      }
      break;
    }
    case Furthest: { // Takes cluster further from any already chosen ones
      while( count < res.length ) {
        double max = 0;
        int index = 0;
        for( int i = 0; i < points.length; i++ ) {
          double sqr = minSqr(res, points[i], _ncats, cd, count);
          if( sqr > max ) {
            max = sqr;
            index = i;
          }
        }
        res[count++] = points[index];
      }
      break;
    }
    default:  throw H2O.fail();
    }
    return res;
  }

  private void randomRow(Vec[] vecs, Random rand, double[] cluster, double[] means, double[] mults) {
    long row = Math.max(0, (long) (rand.nextDouble() * vecs[0].length()) - 1);
    data(cluster, vecs, row, means, mults);
  }

  private static boolean normalize(double sigma) {
    // TODO unify handling of constant columns
    return sigma > 1e-6;
  }

  // Pick most common cat level for each cluster_centers' cat columns
  private static double[][] max_cats(double[][] clusters, long[][][] cats) {
    int K = cats.length;
    int ncats = cats[0].length;
    for( int clu = 0; clu < K; clu++ )
      for( int col = 0; col < ncats; col++ ) // Cats use max level for cluster center
        clusters[clu][col] = ArrayUtils.maxIndex(cats[clu][col]);
    return clusters;
  }

  private static double[][] denormalize(double[][] clusters, int ncats, double[] means, double[] mults) {
    int K = clusters.length;
    int N = clusters[0].length;
    double[][] value = new double[K][N];
    for( int clu = 0; clu < K; clu++ ) {
      System.arraycopy(clusters[clu],0,value[clu],0,N);
      if( mults!=null )         // Reverse normalization
        for( int col = ncats; col < N; col++ )
          value[clu][col] = value[clu][col] / mults[col] + means[col];
    }
    return value;
  }

  private static void data(double[] values, Vec[] vecs, long row, double[] means, double[] mults) {
    for( int i = 0; i < values.length; i++ ) {
      double d = vecs[i].at(row);
      values[i] = data(d, i, means, mults, vecs[i].cardinality());
    }
  }

  private static void data(double[] values, Chunk[] chks, int row, double[] means, double[] mults) {
    for( int i = 0; i < values.length; i++ ) {
      double d = chks[i].at0(row);
      values[i] = data(d, i, means, mults, chks[i].vec().cardinality());
    }
  }

  /**
   * Takes mean if NaN, normalize if requested.
   */
  private static double data(double d, int i, double[] means, double[] mults, int cardinality) {
    if(cardinality == -1) {
      if( Double.isNaN(d) )
        d = means[i];
      if( mults != null ) {
        d -= means[i];
        d *= mults[i];
      }
    } else {
      // TODO: If NaN, then replace with majority class?
      if(Double.isNaN(d))
        d = Math.min(Math.round(means[i]), cardinality-1);
    }
    return d;
  }
}
