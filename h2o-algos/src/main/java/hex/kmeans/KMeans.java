package hex.kmeans;

import hex.Model;
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
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Random;

/**
 * Scalable K-Means++ (KMeans||)<br>
 * http://theory.stanford.edu/~sergei/papers/vldb12-kmpar.pdf<br>
 * http://www.youtube.com/watch?v=cigXAxV3XcY
 */
public class KMeans extends ModelBuilder<KMeansModel,KMeansModel.KMeansParameters,KMeansModel.KMeansOutput> {
  @Override
  public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{
      Model.ModelCategory.Clustering
    };
  }

  public enum Initialization {
    Random, PlusPlus, Furthest, User
  }

  // Number of categorical columns
  private int _ncats;

  // Convergence tolerance
  final private double TOLERANCE = 1e-6;

  // Called from an http request
  public KMeans( KMeansModel.KMeansParameters parms ) { super("K-means",parms); init(false); }

  public ModelBuilderSchema schema() { return new KMeansV2(); }


  /** Start the KMeans training Job on an F/J thread. */
  @Override public Job<KMeansModel> trainModel() {
    return start(new KMeansDriver(), _parms._max_iterations);
  }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".
   *
   *  Validate K, max_iterations and the number of rows.  Precompute the number of
   *  categorical columns. */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    if( _parms._k < 1 || _parms._k > 10000000 ) error("_k", "k must be between 1 and 1e7");
    if( _parms._max_iterations < 0 || _parms._max_iterations > 1000000) error("_max_iterations", " max_iterations must be between 0 and 1e6");
    if( _train == null ) return; // Nothing more to check
    if( _train.numRows() < _parms._k ) error("_k","Cannot make " + _parms._k + " clusters out of " + _train.numRows() + " rows");
    if( null != _parms._user_points ){ // Check dimensions of user-specified centers
      if( _parms._user_points.get().numCols() != _train.numCols() ) {
        error("_user_points","The user-specified points must have the same number of columns (" + _train.numCols() + ") as the training observations");
      }
    }

    new CheckCols() {
      @Override protected boolean filter(Vec v) { return v.isEnum(); }
    }.doIt(_train, "Columns cannot have categorical values: ", expensive);

    // Sort columns, so the categoricals are all up front.  They use a
    // different distance metric than numeric columns.
    Vec vecs[] = _train.vecs();
    int ncats=0, nvecs=vecs.length; // Feature count;
    while( ncats != nvecs ) {
      while( ncats < nvecs && vecs[ncats].isEnum() ) ncats++;
      while( nvecs > 0 && !vecs[nvecs-1].isEnum() ) nvecs--;
      if( ncats < nvecs-1 ) _train.swap(ncats,nvecs-1);
    }
    _ncats = ncats;
  }

  abstract class CheckCols {
    abstract protected boolean filter(Vec v);
    void doIt( Frame f, String msg, boolean expensive ) {
      boolean any=false;
      for( int i = 0; i < f.vecs().length; i++ ) {
        if( filter(f.vecs()[i]) ) {
          if( any ) msg += ", "; // Log cols with errors
          any = true;
          msg += f._names[i];
        }
      }
      if( any ) {
        error("_train", msg);
        if (expensive) Log.info(msg);
      }
    }
  }

  // ----------------------
  private class KMeansDriver extends H2OCountedCompleter<KMeansDriver> {

    // means are used to impute NAs
    double[] prepMeans( final Vec[] vecs) {
      final double[] means = new double[vecs.length];
      for( int i = 0; i < vecs.length; i++ ) means[i] = vecs[i].mean();
      return means;
    }
    // mults & means for standardization
    double[] prepMults( final Vec[] vecs) {
      if( !_parms._standardize ) return null;
      double[] mults = new double[vecs.length];
      for( int i = 0; i < vecs.length; i++ ) {
        double sigma = vecs[i].sigma();
        mults[i] = standardize(sigma) ? 1.0 / sigma : 1.0;
      }
      return mults;
    }

    // Initialize cluster centers
    double[][] initial_centers( KMeansModel model, final Vec[] vecs, final double[] means, final double[] mults ) {
      Random rand = water.util.RandomUtils.getRNG(_parms._seed - 1);
      double centers[][];    // Cluster centers
      if( null != _parms._user_points ) { // User-specified starting points
        int numCenters = _parms._k;
        int numCols = _parms._user_points.get().numCols();
        centers = new double[numCenters][numCols];
        Vec[] centersVecs = _parms._user_points.get().vecs();
        // Get the centers and standardize them if requested
        for (int r=0; r<numCenters; r++) {
          for (int c=0; c<numCols; c++){
            centers[r][c] = centersVecs[c].at(r);
            centers[r][c] = data(centers[r][c], c, means, mults, vecs[c].cardinality());
          }
        }
      }
      else { // Random, Furthest, or PlusPlus initialization
        if (_parms._init == Initialization.Random) {
          // Initialize all cluster centers to random rows
          centers = new double[_parms._k][_train.numCols()];
          for (double[] center : centers)
            randomRow(vecs, rand, center, means, mults);
        } else {
          centers = new double[1][vecs.length];
          // Initialize first cluster center to random row
          randomRow(vecs, rand, centers[0], means, mults);

          while (model._output._iterations < 5) {
            // Sum squares distances to cluster center
            SumSqr sqr = new SumSqr(centers, means, mults, _ncats).doAll(vecs);

            // Sample with probability inverse to square distance
            Sampler sampler = new Sampler(centers, means, mults, _ncats, sqr._sqr, _parms._k * 3, _parms._seed).doAll(vecs);
            centers = ArrayUtils.append(centers, sampler._sampled);

            // Fill in sample centers into the model
            if (!isRunning()) return null; // Stopped/cancelled
            model._output._centers_raw = destandardize(centers, _ncats, means, mults);
            model._output._avg_within_ss = sqr._sqr / _train.numRows();

            model._output._iterations++;     // One iteration done

            model.update(_key); // Make early version of model visible, but don't update progress using update(1)
          }
          // Recluster down to k cluster centers
          centers = recluster(centers, rand);
          model._output._iterations = -1; // Reset iteration count
        }
      }
      return centers;
    }

    // Number of reinitialization attempts for preventing empty clusters
    transient private int _reinit_attempts;
    // Handle the case where some centers go dry.  Rescue only 1 cluster
    // per iteration ('cause we only tracked the 1 worst row)
    boolean cleanupBadClusters( Lloyds task, final Vec[] vecs, final double[][] centers, final double[] means, final double[] mults ) {
      // Find any bad clusters
      int clu;
      for( clu=0; clu<_parms._k; clu++ )
        if( task._size[clu] == 0 ) break;
      if( clu == _parms._k ) return false; // No bad clusters

      long row = task._worst_row;
      Log.warn("KMeans: Re-initializing cluster " + clu + " to row " + row);
      data(centers[clu] = task._cMeans[clu], vecs, row, means, mults);
      task._size[clu] = 1;

      // Find any MORE bad clusters; we only fixed the first one
      for( clu=0; clu<_parms._k; clu++ )
        if( task._size[clu] == 0 ) break;
      if( clu == _parms._k ) return false; // No MORE bad clusters

      // If we see 2 or more bad rows, just re-run Lloyds to get the
      // next-worst row.  We don't count this as an iteration, because
      // we're not really adjusting the centers, we're trying to get
      // some centers *at-all*.
      Log.warn("KMeans: Re-running Lloyds to re-init another cluster");
      if (_reinit_attempts++ < _parms._k) {
        return true;  // Rerun Lloyds, and assign points to centroids
      } else {
        _reinit_attempts = 0;
        return false;
      }
    }

    // Compute all interesting KMeans stats (errors & variances of clusters,
    // etc).  Return new centers.
    double[][] computeStatsFillModel( Lloyds task, KMeansModel model, final Vec[] vecs, final double[][] centers, final double[] means, final double[] mults ) {
      // Fill in the model based on original destandardized centers
      model._output._centers_raw = destandardize(centers, _ncats, means, mults);
      String[] rowHeaders = new String[_parms._k];
      for(int i = 0; i < _parms._k; i++)
        rowHeaders[i] = String.valueOf(i+1);
      String[] colTypes = new String[_train.numCols()];
      String[] colFormats = new String[_train.numCols()];
      Arrays.fill(colTypes, "double");
      Arrays.fill(colFormats, "%5f");
      model._output._centers = new TwoDimTable("Cluster means", rowHeaders, _train.names(), colTypes, colFormats, "", new String[_parms._k][], model._output._centers_raw);
      model._output._size = task._size;
      model._output._within_mse = task._cSqr;
      double ssq = 0;       // sum squared error
      for( int i=0; i<_parms._k; i++ ) {
        ssq += model._output._within_mse[i]; // sum squared error all clusters
        model._output._within_mse[i] /= task._size[i]; // mse within-cluster
      }
      model._output._avg_within_ss = ssq/_train.numRows(); // mse total

      // Sum-of-square distance from grand mean
      if(_parms._k == 1)
        model._output._avg_ss = model._output._avg_within_ss;
      else {
        // If data already standardized, grand mean is just the origin
        TotSS totss = new TotSS(means,mults).doAll(vecs);
        model._output._avg_ss = totss._tss/_train.numRows(); // mse with respect to grand mean
      }
      model._output._avg_between_ss = model._output._avg_ss - model._output._avg_within_ss;  // mse between-cluster
      return task._cMeans;      // New centers
    }

    // Stopping criteria
    boolean isDone( KMeansModel model, double[][] newCenters, double[][] oldCenters ) {
      if( !isRunning() ) return true; // Stopped/cancelled
      // Stopped for running out iterations
      if( model._output._iterations > _parms._max_iterations) return true;

      // Compute average change in standardized cluster centers
      if( oldCenters==null ) return false; // No prior iteration, not stopping
      double average_change = 0;
      for( int clu=0; clu<_parms._k; clu++ )
        average_change += distance(oldCenters[clu],newCenters[clu],_ncats);
      average_change /= _parms._k;  // Average change per cluster
      if( average_change < TOLERANCE ) return true;

      return false;             // Not stopping
    }

    // Main worker thread
    @Override protected void compute2() {

      KMeansModel model = null;
      try {
        _parms.read_lock_frames(KMeans.this); // Fetch & read-lock input frames
        init(true);
        if( error_count() > 0 ) throw new IllegalArgumentException("Found validation errors: "+validationErrors());

        // The model to be built
        model = new KMeansModel(dest(), _parms, new KMeansModel.KMeansOutput(KMeans.this));
        model.delete_and_lock(_key);

        //
        model._output._categorical_column_count = _ncats;
        final Vec vecs[] = _train.vecs();
        // mults & means for standardization
        // means are used to impute NAs
        final double[] means = prepMeans(vecs);
        final double[] mults = prepMults(vecs);
        model._output._normSub = means;
        model._output._normMul = mults;
        // Initialize cluster centers and standardize if requested
        double[][] centers = initial_centers(model,vecs,means,mults);
        if( centers==null ) return; // Stopped/cancelled during center-finding
        double[][] oldCenters = null;

        // ---
        // Run the main KMeans Clustering loop
        // Stop after enough iterations or average_change < TOLERANCE
        while( !isDone(model,centers,oldCenters) ) {
          Lloyds task = new Lloyds(centers,means,mults,_ncats, _parms._k).doAll(vecs);
          // Pick the max categorical level for cluster center
          max_cats(task._cMeans,task._cats);

          // Handle the case where some centers go dry.  Rescue only 1 cluster
          // per iteration ('cause we only tracked the 1 worst row)
          if( cleanupBadClusters(task,vecs,centers,means,mults) ) continue;

          // Compute model stats; update standardized cluster centers
          oldCenters = centers;
          centers = computeStatsFillModel(task,model,vecs,centers,means,mults);

          model._output._iterations++;
          model.update(_key); // Update model in K/V store
          update(1);          // One unit of work
        }
        done();                 // Job done!

      } catch( Throwable t ) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          t.printStackTrace();
          failed(t);
          throw t;
        }
      } finally {
        if( model != null ) model.unlock(_key);
        _parms.read_unlock_frames(KMeans.this);
      }
      tryComplete();
    }
  }

  // -------------------------------------------------------------------------
  // Initial sum-of-square-distance to nearest cluster center
  private static class TotSS extends MRTask<TotSS> {
    // IN
    double[] _means, _mults;

    // OUT
    double _tss;

    TotSS(double[] means, double[] mults) {
      _means = means;
      _mults = mults;
      _tss = 0;
    }

    @Override public void map(Chunk[] cs) {
      for( int row = 0; row < cs[0]._len; row++ ) {
        for( int i = 0; i < cs.length; i++ ) {
          double d = cs[i].atd(row);
          if(Double.isNaN(d)) continue;
          d = (d - _means[i]) * (_mults == null ? 1 : _mults[i]);
          _tss += d * d;
        }
      }
      _means = null;
    }

    @Override public void reduce(TotSS other) { _tss += other._tss; }
  }

  // -------------------------------------------------------------------------
  // Initial sum-of-square-distance to nearest cluster center
  private static class SumSqr extends MRTask<SumSqr> {
    // IN
    double[][] _centers;
    double[] _means, _mults; // Standardization
    final int _ncats;

    // OUT
    double _sqr;

    SumSqr( double[][] centers, double[] means, double[] mults, int ncats ) {
      _centers = centers;
      _means = means;
      _mults = mults;
      _ncats = ncats;
    }

    @Override public void map(Chunk[] cs) {
      double[] values = new double[cs.length];
      ClusterDist cd = new ClusterDist();
      for( int row = 0; row < cs[0]._len; row++ ) {
        data(values, cs, row, _means, _mults);
        _sqr += minSqr(_centers, values, _ncats, cd);
      }
      _means = _mults = null;
      _centers = null;
    }

    @Override public void reduce(SumSqr other) { _sqr += other._sqr; }
  }

  // -------------------------------------------------------------------------
  // Sample rows with increasing probability the farther they are from any
  // cluster center.
  private static class Sampler extends MRTask<Sampler> {
    // IN
    double[][] _centers;
    double[] _means, _mults; // Standardization
    final int _ncats;
    final double _sqr;           // Min-square-error
    final double _probability;   // Odds to select this point
    final long _seed;

    // OUT
    double[][] _sampled;   // New cluster centers

    Sampler( double[][] centers, double[] means, double[] mults, int ncats, double sqr, double prob, long seed ) {
      _centers = centers;
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

      for( int row = 0; row < cs[0]._len; row++ ) {
        data(values, cs, row, _means, _mults);
        double sqr = minSqr(_centers, values, _ncats, cd);
        if( _probability * sqr > rand.nextDouble() * _sqr )
          list.add(values.clone());
      }

      _sampled = new double[list.size()][];
      list.toArray(_sampled);
      _centers = null;
      _means = _mults = null;
    }

    @Override public void reduce(Sampler other) {
      _sampled = ArrayUtils.append(_sampled, other._sampled);
    }
  }

  // ---------------------------------------
  // A Lloyd's pass:
  //   Find nearest cluster center for every point
  //   Compute new mean/center & variance & rows for each cluster
  //   Compute distance between clusters
  //   Compute total sqr distance

  private static class Lloyds extends MRTask<Lloyds> {
    // IN
    double[][] _centers;
    double[] _means, _mults;      // Standardization
    final int _ncats, _k;

    // OUT
    double[][] _cMeans;         // Means for each cluster
    long[/*k*/][/*ncats*/][] _cats; // Histogram of cat levels
    double[] _cSqr;             // Sum of squares for each cluster
    long[] _size;               // Number of rows in each cluster
    long _worst_row;            // Row with max err
    double _worst_err;          // Max-err-row's max-err

    Lloyds( double[][] centers, double[] means, double[] mults, int ncats, int k ) {
      _centers = centers;
      _means = means;
      _mults = mults;
      _ncats = ncats;
      _k = k;
    }

    @Override public void map(Chunk[] cs) {
      int N = cs.length;
      assert _centers[0].length==N;
      _cMeans = new double[_k][N];
      _cSqr = new double[_k];
      _size = new long[_k];
      // Space for cat histograms
      _cats = new long[_k][_ncats][];
      for( int clu=0; clu< _k; clu++ )
        for( int col=0; col<_ncats; col++ )
          _cats[clu][col] = new long[cs[col].vec().cardinality()];
      _worst_err = 0;

      // Find closest cluster center for each row
      double[] values = new double[N];
      ClusterDist cd = new ClusterDist();
      for( int row = 0; row < cs[0]._len; row++ ) {
        data(values, cs, row, _means, _mults);
        closest(_centers, values, _ncats, cd);
        int clu = cd._cluster;
        assert clu != -1; // No broken rows
        _cSqr[clu] += cd._dist;

        // Add values and increment counter for chosen cluster
        for( int col = 0; col < _ncats; col++ )
          _cats[clu][col][(int)values[col]]++; // Histogram the cats
        for( int col = _ncats; col < N; col++ )
          _cMeans[clu][col] += values[col];
        _size[clu]++;
        // Track worst row
        if( cd._dist > _worst_err) { _worst_err = cd._dist; _worst_row = cs[0].start()+row; }
      }
      // Scale back down to local mean
      for( int clu = 0; clu < _k; clu++ )
        if( _size[clu] != 0 ) ArrayUtils.div(_cMeans[clu], _size[clu]);
      _centers = null;
      _means = _mults = null;
    }

    @Override public void reduce(Lloyds mr) {
      for( int clu = 0; clu < _k; clu++ ) {
        long ra =    _size[clu];
        long rb = mr._size[clu];
        double[] ma =    _cMeans[clu];
        double[] mb = mr._cMeans[clu];
        for( int c = 0; c < ma.length; c++ ) // Recursive mean
          if( ra+rb > 0 ) ma[c] = (ma[c] * ra + mb[c] * rb) / (ra + rb);
      }
      ArrayUtils.add(_cats, mr._cats);
      ArrayUtils.add(_cSqr, mr._cSqr);
      ArrayUtils.add(_size, mr._size);
      // track global worst-row
      if( _worst_err < mr._worst_err) { _worst_err = mr._worst_err; _worst_row = mr._worst_row; }
    }
  }

  // A pair result: nearest cluster center and the square distance
  private static final class ClusterDist { int _cluster; double _dist;  }

  private static double minSqr(double[][] centers, double[] point, int ncats, ClusterDist cd) {
    return closest(centers, point, ncats, cd, centers.length)._dist;
  }

  private static double minSqr(double[][] centers, double[] point, int ncats, ClusterDist cd, int count) {
    return closest(centers,point,ncats,cd,count)._dist;
  }

  private static ClusterDist closest(double[][] centers, double[] point, int ncats, ClusterDist cd) {
    return closest(centers, point, ncats, cd, centers.length);
  }

  private static double distance(double[] center, double[] point, int ncats) {
    double sqr = 0;             // Sum of dimensional distances
    int pts = point.length;     // Count of valid points

    // Categorical columns first.  Only equals/unequals matters (i.e., distance is either 0 or 1).
    for(int column = 0; column < ncats; column++) {
        double d = point[column];
      if( Double.isNaN(d) ) pts--;
      else if( d != center[column] )
        sqr += 1.0;           // Manhattan distance
    }
    // Numeric column distance
    for( int column = ncats; column < center.length; column++ ) {
      double d = point[column];
      if( Double.isNaN(d) ) pts--; // Do not count
      else {
        double delta = d - center[column];
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

  /** Return both nearest of N cluster center/centroids, and the square-distance. */
  private static ClusterDist closest(double[][] centers, double[] point, int ncats, ClusterDist cd, int count) {
    int min = -1;
    double minSqr = Double.MAX_VALUE;
    for( int cluster = 0; cluster < count; cluster++ ) {
      double sqr = distance(centers[cluster],point,ncats);
      if( sqr < minSqr ) {      // Record nearest cluster
        min = cluster;
        minSqr = sqr;
      }
    }
    cd._cluster = min;          // Record nearest cluster
    cd._dist = minSqr;          // Record square-distance
    return cd;                  // Return for flow-coding
  }

  // For KMeansModel scoring; just the closest cluster center
  public static int closest(double[][] centers, double[] point, int ncats) {
    int min = -1;
    double minSqr = Double.MAX_VALUE;
    for( int cluster = 0; cluster < centers.length; cluster++ ) {
      double sqr = distance(centers[cluster],point,ncats);
      if( sqr < minSqr ) {      // Record nearest cluster center
        min = cluster;
        minSqr = sqr;
      }
    }
    return min;
  }

  // KMeans++ re-clustering
  private double[][] recluster(double[][] points, Random rand) {
    double[][] res = new double[_parms._k][];
    res[0] = points[0];
    int count = 1;
    ClusterDist cd = new ClusterDist();
    switch( _parms._init ) {
    case Random:
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
    case Furthest: { // Takes cluster center further from any already chosen ones
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

  private void randomRow(Vec[] vecs, Random rand, double[] center, double[] means, double[] mults) {
    long row = Math.max(0, (long) (rand.nextDouble() * vecs[0].length()) - 1);
    data(center, vecs, row, means, mults);
  }

  private static boolean standardize(double sigma) {
    // TODO unify handling of constant columns
    return sigma > 1e-6;
  }

  // Pick most common cat level for each cluster_centers' cat columns
  private static double[][] max_cats(double[][] centers, long[][][] cats) {
    int K = cats.length;
    int ncats = cats[0].length;
    for( int clu = 0; clu < K; clu++ )
      for( int col = 0; col < ncats; col++ ) // Cats use max level for cluster center
        centers[clu][col] = ArrayUtils.maxIndex(cats[clu][col]);
    return centers;
  }

  private static double[][] destandardize(double[][] centers, int ncats, double[] means, double[] mults) {
    int K = centers.length;
    int N = centers[0].length;
    double[][] value = new double[K][N];
    for( int clu = 0; clu < K; clu++ ) {
      System.arraycopy(centers[clu],0,value[clu],0,N);
      if( mults!=null ) {        // Reverse standardization
        for (int col = ncats; col < N; col++)
          value[clu][col] = value[clu][col] / mults[col] + means[col];
      }
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
      double d = chks[i].atd(row);
      values[i] = data(d, i, means, mults, chks[i].vec().cardinality());
    }
  }

  /**
   * Takes mean if NaN, standardize if requested.
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
