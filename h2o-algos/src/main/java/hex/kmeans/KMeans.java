package hex.kmeans;

import hex.*;
import hex.util.LinearAlgebraUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static hex.genmodel.GenModel.Kmeans_preprocessData;

/**
 * Scalable K-Means++ (KMeans||)<br>
 * http://theory.stanford.edu/~sergei/papers/vldb12-kmpar.pdf<br>
 * http://www.youtube.com/watch?v=cigXAxV3XcY
 */
public class KMeans extends ClusteringModelBuilder<KMeansModel,KMeansModel.KMeansParameters,KMeansModel.KMeansOutput> {
  @Override public ToEigenVec getToEigenVec() { return LinearAlgebraUtils.toEigen; }
  // Convergence tolerance
  final static private double TOLERANCE = 1e-4;

  @Override public ModelCategory[] can_build() { return new ModelCategory[]{ ModelCategory.Clustering }; }

  @Override public boolean havePojo() { return true; }
  @Override public boolean haveMojo() { return true; }

  public enum Initialization { Random, PlusPlus, Furthest, User }
  /** Start the KMeans training Job on an F/J thread. */
  @Override protected KMeansDriver trainModelImpl() { return new KMeansDriver();  }

  // Called from an http request
  public KMeans( KMeansModel.KMeansParameters parms         ) { super(parms    ); init(false); }
  public KMeans( KMeansModel.KMeansParameters parms, Job job) { super(parms,job); init(false); }
  public KMeans(boolean startup_once) { super(new KMeansModel.KMeansParameters(),startup_once); }

  @Override protected void checkMemoryFootPrint_impl() {
    long mem_usage = 8 /*doubles*/ * _parms._k * _train.numCols() * (_parms._standardize ? 2 : 1);
    long max_mem = H2O.SELF._heartbeat.get_free_mem();
    if (mem_usage > max_mem) {
      String msg = "Centroids won't fit in the driver node's memory ("
              + PrettyPrint.bytes(mem_usage) + " > " + PrettyPrint.bytes(max_mem)
              + ") - try reducing the number of columns and/or the number of categorical factors.";
      error("_train", msg);
    }
  }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".
   *
   *  Validate K, max_iterations and the number of rows. */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    if(expensive)
      if(_parms._fold_column != null) _train.remove(_parms._fold_column);
    if( _parms._max_iterations <= 0 || _parms._max_iterations > 1e6)
      error("_max_iterations", " max_iterations must be between 1 and 1e6");
    if (_train == null) return;
    if (_parms._init == Initialization.User && _parms._user_points == null)
      error("_user_y","Must specify initial cluster centers");
    if (_parms._user_points != null) { // Check dimensions of user-specified centers
      Frame user_points = _parms._user_points.get();
      if (user_points == null)
        error("_user_y", "User-specified points do not refer to a valid frame");
      else if (user_points.numCols() != _train.numCols() - numSpecialCols())
        error("_user_y", "The user-specified points must have the same number of columns (" + (_train.numCols() -
                numSpecialCols()) + ") as the training observations");
      else if( user_points.numRows() != _parms._k)
        error("_user_y", "The number of rows in the user-specified points is not equal to k = " + _parms._k);
    }
    if (_parms._estimate_k) {
      if (_parms._user_points!=null)
        error("_estimate_k", "Cannot estimate k if user_points are provided.");
      if(_parms._cluster_size_constraints != null){
        error("_estimate_k", "Cannot estimate k if cluster_size_constraints are provided.");
      }
      info("_seed", "seed is ignored when estimate_k is enabled.");
      info("_init", "Initialization scheme is ignored when estimate_k is enabled - algorithm is deterministic.");
      if (expensive) {
        boolean numeric = false;
        for (Vec v : _train.vecs()) {
          if (v.isNumeric()) {
            numeric = true;
            break;
          }
        }
        if (!numeric) {
          error("_estimate_k", "Cannot estimate k if data has no numeric columns.");
        }
      }
    }
    if(_parms._cluster_size_constraints != null){
      if(_parms._cluster_size_constraints.length != _parms._k){
        error("_cluster_size_constraints", "\"The number of cluster size constraints is not equal to k = \" + _parms._k");
      }
    }
    if(_parms._fold_assignment == Model.Parameters.FoldAssignmentScheme.Stratified){
      error("fold_assignment", "K-means is an unsupervised algorithm; the stratified fold assignment cannot be used because of the missing response column.");
    }
    if (expensive && error_count() == 0) checkMemoryFootPrint();
  }

  public void cv_makeAggregateModelMetrics(ModelMetrics.MetricBuilder[] mbs){
    super.cv_makeAggregateModelMetrics(mbs);
    ((ModelMetricsClustering.MetricBuilderClustering) mbs[0])._within_sumsqe = null;
    ((ModelMetricsClustering.MetricBuilderClustering) mbs[0])._size = null;
  }

  // ----------------------
  private final class KMeansDriver extends Driver {
    private String[][] _isCats;  // Categorical columns

    // Initialize cluster centers
    double[][] initial_centers(KMeansModel model, final Vec[] vecs, final double[] means, final double[] mults, final int[] modes, int k) {

      // Categoricals use a different distance metric than numeric columns.
      model._output._categorical_column_count=0;
      _isCats = new String[vecs.length][];
      for( int v=0; v<vecs.length; v++ ) {
        _isCats[v] = vecs[v].isCategorical() ? new String[0] : null;
        if (_isCats[v] != null) model._output._categorical_column_count++;
      }
      Random rand = water.util.RandomUtils.getRNG(_parms._seed-1);
      double centers[][];    // Cluster centers
      if( null != _parms._user_points ) { // User-specified starting points
        Frame user_points = _parms._user_points.get();
        int numCenters = (int)user_points.numRows();
        int numCols = model._output.nfeatures();
        centers = new double[numCenters][numCols];
        Vec[] centersVecs = user_points.vecs();
        // Get the centers and standardize them if requested
        for (int r=0; r<numCenters; r++) {
          for (int c=0; c<numCols; c++){
            centers[r][c] = centersVecs[c].at(r);
            centers[r][c] = Kmeans_preprocessData(centers[r][c], c, means, mults, modes);
          }
        }
      }
      else { // Random, Furthest, or PlusPlus initialization
        if (_parms._init == Initialization.Random) {
          // Initialize all cluster centers to random rows
          centers = new double[k][model._output.nfeatures()];
          for (double[] center : centers)
            randomRow(vecs, rand, center, means, mults, modes);
        } else {
          centers = new double[1][model._output.nfeatures()];
          // Initialize first cluster center to random row
          randomRow(vecs, rand, centers[0], means, mults, modes);

          model._output._iterations = 0;
          while (model._output._iterations < 5) {
            // Sum squares distances to cluster center
            SumSqr sqr = new SumSqr(centers, means, mults, modes, _isCats).doAll(vecs);

            // Sample with probability inverse to square distance
            Sampler sampler = new Sampler(centers, means, mults, modes, _isCats, sqr._sqr, k * 3, _parms.getOrMakeRealSeed(), hasWeightCol()).doAll(vecs);
            centers = ArrayUtils.append(centers, sampler._sampled);

            // Fill in sample centers into the model
            model._output._centers_raw = destandardize(centers, _isCats, means, mults);
            model._output._tot_withinss = sqr._sqr / _train.numRows();

            model._output._iterations++;     // One iteration done

            model.update(_job); // Make early version of model visible, but don't update progress using update(1)
            if (stop_requested()) {
              if (timeout())
                warn("_max_runtime_secs reached.", "KMeans exited before finishing all iterations.");
              break; // Stopped/cancelled
            }
          }
          // Recluster down to k cluster centers
          centers = recluster(centers, rand, k, _parms._init, _isCats);
          model._output._iterations = 0; // Reset iteration count
        }
      }
      assert(centers.length == k);
      return centers;
    }

    // Number of reinitialization attempts for preventing empty clusters
    transient private int _reinit_attempts;
    // Handle the case where some centers go dry.  Rescue only 1 cluster
    // per iteration ('cause we only tracked the 1 worst row)
    boolean cleanupBadClusters( IterationTask task, final Vec[] vecs, final double[][] centers, final double[] means, final double[] mults, final int[] modes ) {
      // Find any bad clusters
      int clu;
      for( clu=0; clu<centers.length; clu++ )
        if( task._size[clu] == 0 ) break;
      if( clu == centers.length ) return false; // No bad clusters

      long row = task._worst_row;
      Log.warn("KMeans: Re-initializing cluster " + clu + " to row " + row);
      data(centers[clu] = task._cMeans[clu], vecs, row, means, mults, modes);
      task._size[clu] = 1; //FIXME: PUBDEV-871 Some other cluster had their membership count reduced by one! (which one?)

      // Find any MORE bad clusters; we only fixed the first one
      for( clu=0; clu<centers.length; clu++ )
        if( task._size[clu] == 0 ) break;
      if( clu == centers.length ) return false; // No MORE bad clusters

      // If we see 2 or more bad rows, just re-run Lloyds to get the
      // next-worst row.  We don't count this as an iteration, because
      // we're not really adjusting the centers, we're trying to get
      // some centers *at-all*.
      Log.warn("KMeans: Re-running Lloyds to re-init another cluster");
      if (_reinit_attempts++ < centers.length) {
        return true;  // Rerun Lloyds, and assign points to centroids
      } else {
        _reinit_attempts = 0;
        return false;
      }
    }

    // Compute all interesting KMeans stats (errors & variances of clusters,
    // etc).  Return new centers.
    double[][] computeStatsFillModel(IterationTask task, KMeansModel model, final Vec[] vecs, final double[] means, final double[] mults, final int[] modes, int k) {
      // Fill in the model based on original destandardized centers
      if (model._parms._standardize) {
        model._output._centers_std_raw = task._cMeans;
      }
      model._output._centers_raw = destandardize(task._cMeans, _isCats, means, mults);
      model._output._size = task._size;
      model._output._withinss = task._cSqr;
      double ssq = 0;       // sum squared error
      for( int i=0; i<k; i++ )
        ssq += model._output._withinss[i]; // sum squared error all clusters
      model._output._tot_withinss = ssq;

      // Sum-of-square distance from grand mean
      if(k == 1) {
        model._output._totss = model._output._tot_withinss;
      }
      else {
        // If data already standardized, grand mean is just the origin
        TotSS totss = new TotSS(means,mults,modes, train().domains(), train().cardinality()).doAll(vecs);
        model._output._totss = totss._tss;
      }
      model._output._betweenss = model._output._totss - model._output._tot_withinss;  // MSE between-cluster
      model._output._iterations++;

      model._output._history_withinss = ArrayUtils.copyAndFillOf( model._output._history_withinss, model._output._history_withinss.length+1, model._output._tot_withinss);
      model._output._k = ArrayUtils.copyAndFillOf(model._output._k, model._output._k.length+1, k);
      model._output._training_time_ms = ArrayUtils.copyAndFillOf(model._output._training_time_ms, model._output._training_time_ms.length+1, System.currentTimeMillis());
      model._output._reassigned_count = ArrayUtils.copyAndFillOf(model._output._reassigned_count, model._output._reassigned_count.length+1, task._reassigned_count);

      // Two small TwoDimTables - cheap
      model._output._model_summary = createModelSummaryTable(model._output);
      model._output._scoring_history = createScoringHistoryTable(model._output);

      // Take the cluster stats from the model, and assemble them into a model metrics object
      model._output._training_metrics = makeTrainingMetrics(model);

      return task._cMeans;      // New centers
    }

    // Main worker thread
    @Override
    public void computeImpl() {
      KMeansModel model = null;
      Key bestOutputKey = Key.make();
      try {
        init(true);
        // Do lock even before checking the errors, since this block is finalized by unlock
        // (not the best solution, but the code is more readable)
        // Something goes wrong
        if( error_count() > 0 ) throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(KMeans.this);
        // The model to be built
        // Set fold_column to null and will be added back into model parameter after
        String fold_column = _parms._fold_column;
        _parms._fold_column = null;
        model = new KMeansModel(dest(), _parms, new KMeansModel.KMeansOutput(KMeans.this));
        model.delete_and_lock(_job);

        int startK = _parms._estimate_k ? 1 : _parms._k;
        
        final Vec vecs[] = _train.vecs();
        // mults & means for standardization
        final double[] means = _train.means();  // means are used to impute NAs
        final double[] mults = _parms._standardize ? _train.mults() : null;
        final int   [] impute_cat = new int[vecs.length];
        for(int i = 0; i < vecs.length; i++)
          impute_cat[i] = vecs[i].isCategorical() ? DataInfo.imputeCat(vecs[i],true) : -1;
        model._output._normSub = means;
        model._output._normMul = mults;
        model._output._mode = impute_cat;
        // Initialize cluster centers and standardize if requested
        double[][] centers = initial_centers(model,vecs,means,mults,impute_cat, startK);
        if( centers==null ) return; // Stopped/cancelled during center-finding
        boolean work_unit_iter = !_parms._estimate_k;
        boolean constrained = _parms._cluster_size_constraints != null;

        // ---
        // Run the main KMeans Clustering loop
        // Stop after enough iterations or reassigned_count < TOLERANCE * num_rows
        double sum_squares = 0;
        final double rel_improvement_cutoff = Math.min(0.02 + 10. / _train.numRows() + 2.5 / Math.pow(model._output.nfeatures(), 2), 0.8);
        if (_parms._estimate_k)
          Log.info("Cutoff for relative improvement in within_cluster_sum_of_squares: " + rel_improvement_cutoff);

        Vec[] vecs2;
        long csum = 0;
        if(!constrained) {
          vecs2 = Arrays.copyOf(vecs, vecs.length+1);
          vecs2[vecs2.length-1] = vecs2[0].makeCon(-1);
        } else {
          int newVecLength = vecs.length + 2 * centers.length + 3; // data (+ weight column) + distances + edge indices + result distance + old assignment + new assignment
          vecs2 = Arrays.copyOf(vecs, newVecLength);
          for (int i = vecs.length; i < newVecLength; i++) {
            vecs2[i] = vecs2[0].makeCon(Double.MAX_VALUE);
          }
          // Check sum of constrains
          for(int i = 0; i<_parms._cluster_size_constraints.length; i++){
            assert _parms._cluster_size_constraints[i] > 0: "The value of constraint should be higher then zero.";
            csum += _parms._cluster_size_constraints[i];
            assert csum <= vecs[0].length(): "The sum of constraints ("+csum+") is higher than the number of data rows ("+vecs[0].length()+").";
          }
        }
        
        for (int k = startK; k <= _parms._k; ++k) {
          if(!constrained){
            Log.info("Running Lloyds iteration for " + k + " centroids.");
          } else {
            Log.info("Running Constrained K-means iteration for " + k + " centroids.");
          }
          model._output._iterations = 0;  // Loop ends only when iterations > max_iterations with strict inequality
          double[][] lo=null, hi=null;
          boolean stop = false;
          do {
            assert(centers.length == k);
            IterationTask task;
            if(!constrained) {
              //Lloyds algorithm
              task = new LloydsIterationTask(centers, means, mults, impute_cat, _isCats, k, hasWeightCol()).doAll(vecs2); //1 PASS OVER THE DATA
            }  else {
              // Constrained K-means
              
              // Get distances and aggregated values
              CalculateDistancesTask countDistancesTask = new CalculateDistancesTask(centers, means, mults, impute_cat, _isCats, k, hasWeightCol()).doAll(vecs2);
              
              // Check if the constraint setting does not break cross validation setting
              assert !hasWeightCol() || csum <= countDistancesTask._non_zero_weights : "The sum of constraints ("+csum+") is higher than the number of data rows with non zero weights ("+countDistancesTask._non_zero_weights+") because cross validation is set.";
              
              // Calculate center assignments
              // Experimental code. Polynomial implementation - slow performance. Need to be parallelize!
              
              KMeansSimplexSolver solver = new KMeansSimplexSolver(_parms._cluster_size_constraints, new Frame(vecs2), countDistancesTask._sum, hasWeightCol(), countDistancesTask._non_zero_weights);
              
              // Get cluster assignments
              Frame result = solver.assignClusters();
              
              // Count statistics and result task
              task = new CalculateMetricTask(centers, means, mults, impute_cat, _isCats, k, hasWeightCol()).doAll(result);
            }
            
            // Pick the max categorical level for cluster center
            max_cats(task._cMeans, task._cats, _isCats);

            // Handle the case where some centers go dry.  Rescue only 1 cluster
            // per iteration ('cause we only tracked the 1 worst row)
            // If constrained K-meas is set, clusters with zero points are allowed
            if(!_parms._estimate_k && _parms._cluster_size_constraints == null && cleanupBadClusters(task,vecs,centers,means,mults,impute_cat) ) continue;

            // Compute model stats; update standardized cluster centers
            centers = computeStatsFillModel(task, model, vecs, means, mults, impute_cat, k);
            if (model._parms._score_each_iteration) 
              Log.info(model._output._model_summary);
            lo = task._lo;
            hi = task._hi;

            if (work_unit_iter) {
              model.update(_job); // Update model in K/V store
              _job.update(1); //1 more iteration
            }

            stop = (task._reassigned_count < Math.max(1,train().numRows()*TOLERANCE) ||
                    model._output._iterations >= _parms._max_iterations || stop_requested());
            if (stop) {
              if (model._output._iterations < _parms._max_iterations)
                Log.info("K-means converged after " + model._output._iterations + " iterations.");
              else
                Log.info("K-means stopped after " + model._output._iterations + " iterations.");
            }
          } while (!stop);

          double sum_squares_now = model._output._tot_withinss;
          double rel_improvement;
          if (sum_squares==0) {
            rel_improvement = 1;
          } else {
            rel_improvement = (sum_squares - sum_squares_now) / sum_squares;
          }
          Log.info("Relative improvement in total withinss: " + rel_improvement);
          sum_squares = sum_squares_now;
          if (_parms._estimate_k && k > 1) {
            boolean outerConverged = rel_improvement < rel_improvement_cutoff;
            if (outerConverged) {
              KMeansModel.KMeansOutput best = DKV.getGet(bestOutputKey);
              model._output = best;
              Log.info("Converged. Retrieving the best model with k=" + model._output._k[model._output._k.length-1]);
              break;
            }
          }
          if (!work_unit_iter) {
            DKV.put(bestOutputKey, IcedUtils.deepCopy(model._output)); //store a clone to avoid sharing the state between DKV and here
            model.update(_job); // Update model in K/V store
            _job.update(1); //1 more round for auto-clustering
          }
          if (lo != null && hi != null && _parms._estimate_k)
            centers = splitLargestCluster(centers, lo, hi, means, mults, impute_cat, vecs2, k);
        } //k-finder
        vecs2[vecs2.length-1].remove();
        
        // Create metrics by scoring on training set otherwise scores are based on last Lloyd iteration
        // These lines cause the training metrics are recalculated on strange model values.
        // Especially for Constrained Kmeans, it returns a result that does not meet the constraints set
        // because scoring is based on calculated centroids and does not preserve the constraints
        // There is a GH issue to explore this part of code: https://github.com/h2oai/h2o-3/issues/8543
        if(!constrained) {
          model.score(_parms.train()).delete();
          model._output._training_metrics = ModelMetrics.getFromDKV(model,_parms.train());
        }

        model.update(_job); // Update model in K/V store
        Log.info(model._output._model_summary);
        Log.info(model._output._scoring_history);
        Log.info(((ModelMetricsClustering)model._output._training_metrics).createCentroidStatsTable().toString());

        // At the end: validation scoring (no need to gather scoring history)
        if (_valid != null) {
          model.score(_parms.valid()).delete(); //this appends a ModelMetrics on the validation set
          model._output._validation_metrics = ModelMetrics.getFromDKV(model,_parms.valid());
        }
        model._parms._fold_column = fold_column;
        model.update(_job); // Update model in K/V store
      } finally {
        if( model != null ) model.unlock(_job);
        DKV.remove(bestOutputKey);
      }
    }

    double[][] splitLargestCluster(double[][] centers, double[][] lo, double[][] hi, double[] means, double[] mults, int[] impute_cat, Vec[] vecs2, int k) {
      double[][] newCenters = Arrays.copyOf(centers, centers.length + 1);
      for (int i = 0; i < centers.length; ++i)
        newCenters[i] = centers[i].clone();

      double maxRange=0;
      int clusterToSplit=0;
      int dimToSplit=0;
      for (int i = 0; i < centers.length; ++i) {
        double[] range = new double[hi[i].length];
        for( int col=0; col<hi[i].length; col++ ) {
          if (_isCats[col]!=null) continue; // can't split a cluster along categorical direction
          range[col] = hi[i][col] - lo[i][col];
          if ((float)range[col] > (float)maxRange) { //break ties
            clusterToSplit = i;
            dimToSplit = col;
            maxRange = range[col];
          }
        }
//        Log.info("Range for cluster " + i + ": " + Arrays.toString(range));
      }
      // start out new centroid as a copy of the one to split
      assert (_isCats[dimToSplit] == null);
      double splitPoint = newCenters[clusterToSplit][dimToSplit];
//      Log.info("Splitting cluster " + clusterToSplit + " in half in dimension " + dimToSplit + " at splitpoint: " + splitPoint);

      // compute the centroids of the two sub-clusters
      SplitTask task = new SplitTask(newCenters, means, mults, impute_cat, _isCats, k+1, hasWeightCol(), clusterToSplit, dimToSplit, splitPoint).doAll(vecs2);
//      Log.info("Splitting: " + Arrays.toString(newCenters[clusterToSplit]));
      newCenters[clusterToSplit]      = task._cMeans[clusterToSplit].clone();
//      Log.info("Into One: " + Arrays.toString(newCenters[clusterToSplit]));
      newCenters[newCenters.length-1] = task._cMeans[newCenters.length-1].clone();
//      Log.info("     Two: " + Arrays.toString(newCenters[newCenters.length-1]));
      return newCenters;
    }

    private TwoDimTable createModelSummaryTable(KMeansModel.KMeansOutput output) {
      List<String> colHeaders = new ArrayList<>();
      List<String> colTypes = new ArrayList<>();
      List<String> colFormat = new ArrayList<>();
      colHeaders.add("Number of Rows"); colTypes.add("long"); colFormat.add("%d");
      colHeaders.add("Number of Clusters"); colTypes.add("long"); colFormat.add("%d");
      colHeaders.add("Number of Categorical Columns"); colTypes.add("long"); colFormat.add("%d");
      colHeaders.add("Number of Iterations"); colTypes.add("long"); colFormat.add("%d");
      colHeaders.add("Within Cluster Sum of Squares"); colTypes.add("double"); colFormat.add("%.5f");
      colHeaders.add("Total Sum of Squares"); colTypes.add("double"); colFormat.add("%.5f");
      colHeaders.add("Between Cluster Sum of Squares"); colTypes.add("double"); colFormat.add("%.5f");

      final int rows = 1;
      TwoDimTable table = new TwoDimTable(
              "Model Summary", null,
              new String[rows],
              colHeaders.toArray(new String[0]),
              colTypes.toArray(new String[0]),
              colFormat.toArray(new String[0]),
              "");
      int row = 0;
      int col = 0;
      table.set(row, col++, Math.round(_train.numRows() * (hasWeightCol() ? _train.lastVec().mean() : 1)));
      table.set(row, col++, output._centers_raw.length);
      table.set(row, col++, output._categorical_column_count);
      table.set(row, col++, output._k.length-1);
      table.set(row, col++, output._tot_withinss);
      table.set(row, col++, output._totss);
      table.set(row, col++, output._betweenss);
      return table;
    }

    private TwoDimTable createScoringHistoryTable(KMeansModel.KMeansOutput output) {
      List<String> colHeaders = new ArrayList<>();
      List<String> colTypes = new ArrayList<>();
      List<String> colFormat = new ArrayList<>();
      colHeaders.add("Timestamp"); colTypes.add("string"); colFormat.add("%s");
      colHeaders.add("Duration"); colTypes.add("string"); colFormat.add("%s");
      colHeaders.add("Iterations"); colTypes.add("long"); colFormat.add("%d");
      if (_parms._estimate_k) {
        colHeaders.add("Number of Clusters");
        colTypes.add("long");
        colFormat.add("%d");
      }
      colHeaders.add("Number of Reassigned Observations"); colTypes.add("long"); colFormat.add("%d");
      colHeaders.add("Within Cluster Sum Of Squares"); colTypes.add("double"); colFormat.add("%.5f");

      final int rows = output._history_withinss.length;
      TwoDimTable table = new TwoDimTable(
              "Scoring History", null,
              new String[rows],
              colHeaders.toArray(new String[0]),
              colTypes.toArray(new String[0]),
              colFormat.toArray(new String[0]),
              "");
      int row = 0;
      for( int i = 0; i<rows; i++ ) {
        int col = 0;
        assert(row < table.getRowDim());
        assert(col < table.getColDim());
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        table.set(row, col++, fmt.print(output._training_time_ms[i]));
        table.set(row, col++, PrettyPrint.msecs(output._training_time_ms[i]-_job.start_time(), true));
        table.set(row, col++, i);
        if (_parms._estimate_k)
          table.set(row, col++, output._k[i]);
        table.set(row, col++, output._reassigned_count[i]);
        table.set(row, col++, output._history_withinss[i]);
        row++;
      }
      return table;
    }
  }

  // -------------------------------------------------------------------------
  // Initial sum-of-square-distance to nearest cluster center
  private static class TotSS extends MRTask<TotSS> {
    // IN
    final double[] _means, _mults;
    final int[] _modes;
    final String[][] _isCats;
    final int[] _card;

    // OUT
    double _tss;
    double[] _gc; // Grand center (mean of cols)

    TotSS(double[] means, double[] mults, int[] modes, String[][] isCats, int[] card) {
      _means = means;
      _mults = mults;
      _modes = modes;
      _tss = 0;
      _isCats = isCats;
      _card = card;

      // Mean of numeric col is zero when standardized
      _gc = mults!=null ? new double[means.length] : Arrays.copyOf(means, means.length);
      for(int i=0; i<means.length; i++) {
        if(isCats[i] != null)
          _gc[i] = _modes[i];
      }
    }

    @Override public void map(Chunk[] cs) {
      for( int row = 0; row < cs[0]._len; row++ ) {
        double[] values = new double[cs.length];
        // fetch the data - using consistent NA and categorical data handling (same as for training)
        data(values, cs, row, _means, _mults, _modes);
        // compute the distance from the (standardized) cluster centroids
        _tss += hex.genmodel.GenModel.KMeans_distance(_gc, values, _isCats);
      }
    }

    @Override public void reduce(TotSS other) { _tss += other._tss; }
  }

  // -------------------------------------------------------------------------
  // Initial sum-of-square-distance to nearest cluster center
  private static class SumSqr extends MRTask<SumSqr> {
    // IN
    double[][] _centers;
    double[] _means, _mults; // Standardization
    int[] _modes;   // Imputation of missing categoricals
    final String[][] _isCats;

    // OUT
    double _sqr;

    SumSqr( double[][] centers, double[] means, double[] mults, int[] modes, String[][] isCats ) {
      _centers = centers;
      _means = means;
      _mults = mults;
      _modes = modes;
      _isCats = isCats;
    }

    @Override public void map(Chunk[] cs) {
      double[] values = new double[cs.length];
      ClusterDist cd = new ClusterDist();
      for( int row = 0; row < cs[0]._len; row++ ) {
        data(values, cs, row, _means, _mults, _modes);
        _sqr += minSqr(_centers, values, _isCats, cd);
      }
      _means = _mults = null;
      _modes = null;
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
    int[] _modes;     // Imputation of missing categoricals
    final String[][] _isCats;
    final double _sqr;           // Min-square-error
    final double _probability;   // Odds to select this point
    final long _seed;
    boolean _hasWeight;

    // OUT
    double[][] _sampled;   // New cluster centers

    Sampler( double[][] centers, double[] means, double[] mults, int[] modes, String[][] isCats, double sqr, double prob, long seed, boolean hasWeight ) {
      _centers = centers;
      _means = means;
      _mults = mults;
      _modes = modes;
      _isCats = isCats;
      _sqr = sqr;
      _probability = prob;
      _seed = seed;
      _hasWeight = hasWeight;
    }

    @Override public void map(Chunk[] cs) {
      int N = cs.length - (_hasWeight?1:0);
      double[] values = new double[N];
      ArrayList<double[]> list = new ArrayList<>();
      Random rand = RandomUtils.getRNG(0);
      ClusterDist cd = new ClusterDist();

      for( int row = 0; row < cs[0]._len; row++ ) {
        rand.setSeed(_seed + cs[0].start()+row);
        data(values, cs, row, _means, _mults, _modes);
        double sqr = minSqr(_centers, values, _isCats, cd);
        if( _probability * sqr > rand.nextDouble() * _sqr )
          list.add(values.clone());
      }

      _sampled = new double[list.size()][];
      list.toArray(_sampled);
      _centers = null;
      _means = _mults = null;
      _modes = null;
    }

    @Override public void reduce(Sampler other) {
      _sampled = ArrayUtils.append(_sampled, other._sampled);
    }
  }
  
  public static class IterationTask extends MRTask<IterationTask> {
    // IN
    double[][] _centers;
    double[] _means, _mults;      // Standardization
    int[] _modes;   // Imputation of missing categoricals
    final int _k;
    final String[][] _isCats;
    boolean _hasWeight;

    // OUT
    double[][] _lo, _hi;        // Bounding box
    double _reassigned_count;
    double[][] _cMeans;         // Means for each cluster
    long[/*k*/][/*features*/][/*nfactors*/] _cats; // Histogram of cat levels
    double[] _cSqr;             // Sum of squares for each cluster
    long[] _size;               // Number of rows in each cluster
    long _worst_row;            // Row with max err
    double _worst_err;          // Max-err-row's max-err

    IterationTask(double[][] centers, double[] means, double[] mults, int[] modes, String[][] isCats, int k, boolean hasWeight ) {
      _centers = centers;
      _means = means;
      _mults = mults;
      _modes = modes;
      _isCats = isCats;
      _k = k;
      _hasWeight = hasWeight;
    }
  }

  // ---------------------------------------
  // A Lloyd's pass:
  //   Find nearest cluster center for every point
  //   Compute new mean/center & variance & rows for each cluster
  //   Compute distance between clusters
  //   Compute total sqr distance

  private static class LloydsIterationTask extends IterationTask {
    
    LloydsIterationTask(double[][] centers, double[] means, double[] mults, int[] modes, String[][] isCats, int k, boolean hasWeight ) {
      super(centers, means, mults, modes, isCats, k, hasWeight);
    }

    @Override public void map(Chunk[] cs) {
      int N = cs.length - (_hasWeight ? 1:0) - 1 /*clusterassignment*/;
      assert _centers[0].length==N;
      _lo = new double[_k][N];
      for( int clu=0; clu< _k; clu++ )
        Arrays.fill(_lo[clu], Double.MAX_VALUE);
      _hi = new double[_k][N];
      for( int clu=0; clu< _k; clu++ )
        Arrays.fill(_hi[clu], -Double.MAX_VALUE);
      _cMeans = new double[_k][N];
      _cSqr = new double[_k];
      _size = new long[_k];
      // Space for cat histograms
      _cats = new long[_k][N][];
      for( int clu=0; clu< _k; clu++ )
        for( int col=0; col<N; col++ )
          _cats[clu][col] = _isCats[col]==null ? null : new long[cs[col].vec().cardinality()];
      _worst_err = 0;

      Chunk assignment = cs[cs.length-1];
      // Find closest cluster center for each row
      double[] values = new double[N]; // Temp data to hold row as doubles
      ClusterDist cd = new ClusterDist();
      for( int row = 0; row < cs[0]._len; row++ ) {
        double weight = _hasWeight ? cs[N].atd(row) : 1;
        if (weight == 0) continue; //skip holdout rows
        assert(weight == 1); //K-Means only works for weight 1 (or weight 0 for holdout)
        data(values, cs, row, _means, _mults, _modes); // Load row as doubles
        closest(_centers, values, _isCats, cd); // Find closest cluster center
        if (cd._cluster != assignment.at8(row)) {
          _reassigned_count+=weight;
          assignment.set(row, cd._cluster);
        }
        for( int clu=0; clu< _k; clu++ ) {
          for( int col=0; col<N; col++ ) {
            if (cd._cluster == clu) {
              _lo[clu][col] = Math.min(values[col], _lo[clu][col]);
              _hi[clu][col] = Math.max(values[col], _hi[clu][col]);
            }
          }
        }
        int clu = cd._cluster;
        assert clu != -1;       // No broken rows
        _cSqr[clu] += cd._dist;

        // Add values and increment counter for chosen cluster
        for( int col = 0; col < N; col++ )
          if( _isCats[col] != null )
            _cats[clu][col][(int)values[col]]++; // Histogram the cats
          else
            _cMeans[clu][col] += values[col]; // Sum the column centers
        _size[clu]++;
        // Track worst row
        if( cd._dist > _worst_err) { _worst_err = cd._dist; _worst_row = cs[0].start()+row; }
      }
      // Scale back down to local mean
      for( int clu = 0; clu < _k; clu++ )
        if( _size[clu] != 0 ) ArrayUtils.div(_cMeans[clu], _size[clu]);
      _centers = null;
      _means = _mults = null;
      _modes = null;
    }

    @Override public void reduce(IterationTask mr) {
      _reassigned_count += mr._reassigned_count;
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
      for( int clu=0; clu< _k; clu++ ) {
        for( int col=0; col<_lo[clu].length; col++ ) {
          _lo[clu][col] = Math.min(mr._lo[clu][col], _lo[clu][col]);
          _hi[clu][col] = Math.max(mr._hi[clu][col], _hi[clu][col]);
        }
      }
      // track global worst-row
      if( _worst_err < mr._worst_err) { _worst_err = mr._worst_err; _worst_row = mr._worst_row; }
    }
  }

  private static class CalculateDistancesTask extends MRTask<CalculateDistancesTask> {
    // IN
    double[][] _centers;
    double[] _means, _mults;      // Standardization
    int[] _modes;   // Imputation of missing categoricals
    final int _k;
    boolean _hasWeight;
    final String[][] _isCats;
    double _sum;
    long _non_zero_weights;
    
    CalculateDistancesTask(double[][] centers, double[] means, double[] mults, int[] modes, String[][] isCats, int k, boolean hasWeight) {
      _centers = centers;
      _means = means;
      _mults = mults;
      _modes = modes;
      _k = k;
      _hasWeight = hasWeight;
      _isCats = isCats;
      _sum = 0;
      _non_zero_weights = 0;
    }

    @Override
    public void map(Chunk[] cs) {
      int N = cs.length - (_hasWeight ? 1 : 0) - 3 - 2*_centers.length /*data + weight column + distances + edge indices + old assignment + new assignment  */;
      assert _centers[0].length == N;
      int vecsStart = _hasWeight ? N+1 : N;
      
      double[] values = new double[N]; // Temp data to hold row as doubles
      for (int row = 0; row < cs[0]._len; row++) {
        double weight = _hasWeight ? cs[N].atd(row) : 1;
        if (weight == 0) continue; //skip holdout rows
        _non_zero_weights++;
        assert (weight == 1); //K-Means only works for weight 1 (or weight 0 for holdout)
        data(values, cs, row, _means, _mults, _modes); // Load row as doubles
        double[] distances = getDistances(_centers, values, _isCats);
        for(int cluster=0; cluster<distances.length; cluster++){
          double tmpDist = distances[cluster];
          cs[vecsStart+cluster].set(row, tmpDist);
          _sum += tmpDist;
        }
      }
    }

    @Override
    public void reduce(CalculateDistancesTask mrt) {
      _sum += mrt._sum;
      _non_zero_weights += mrt._non_zero_weights;
    }
  }

  private static class CalculateMetricTask extends IterationTask {

    CalculateMetricTask(double[][] centers, double[] means, double[] mults, int[] modes, String[][] isCats, int k, boolean hasWeight) {
      super(centers, means, mults, modes, isCats, k, hasWeight);
    }

    @Override public void map(Chunk[] cs) {
      int N = cs.length - (_hasWeight ? 1:0) - 3 /*clusterassignment*/;
      assert _centers[0].length==N;
      _lo = new double[_k][N];
      for( int clu=0; clu< _k; clu++ )
        Arrays.fill(_lo[clu], Double.MAX_VALUE);
      _hi = new double[_k][N];
      for( int clu=0; clu< _k; clu++ )
        Arrays.fill(_hi[clu], -Double.MAX_VALUE);
      _cMeans = new double[_k][N];
      _cSqr = new double[_k];
      _size = new long[_k];
      // Space for cat histograms
      _cats = new long[_k][N][];
      for( int clu=0; clu< _k; clu++ )
        for( int col=0; col<N; col++ )
          _cats[clu][col] = _isCats[col]==null ? null : new long[cs[col].vec().cardinality()];
      _worst_err = 0;

      Chunk distances = cs[cs.length-3];
      Chunk oldAssignments = cs[cs.length-2];
      Chunk newAssignments = cs[cs.length-1];
      // Find closest cluster center for each row
      double[] values = new double[N]; // Temp data to hold row as doubles
      for( int row = 0; row < cs[0]._len; row++ ) {
        double weight = _hasWeight ? cs[N].atd(row) : 1;
        if (weight == 0) continue; //skip holdout rows
        assert(weight == 1); //K-Means only works for weight 1 (or weight 0 for holdout)
        data(values, cs, row, _means, _mults, _modes); // Load row as doubles
        int cluster = (int) newAssignments.at8(row);
        double distance = distances.atd(row);
        if (cluster != oldAssignments.at8(row)) {
          _reassigned_count+=weight;
          oldAssignments.set(row, cluster);
        }
        for( int clu=0; clu< _k; clu++ ) {
          for( int col=0; col<N; col++ ) {
            if (cluster == clu) {
              _lo[clu][col] = Math.min(values[col], _lo[clu][col]);
              _hi[clu][col] = Math.max(values[col], _hi[clu][col]);
            }
          }
        }
        assert cluster != -1 : "cluster "+cluster+" is not set for row "+row;       // No broken rows
        _cSqr[cluster] += distance;

        // Add values and increment counter for chosen cluster
        for( int col = 0; col < N; col++ )
          if( _isCats[col] != null )
            _cats[cluster][col][(int)values[col]]++; // Histogram the cats
          else
            _cMeans[cluster][col] += values[col]; // Sum the column centers
        _size[cluster]++;
        // Track worst row
        if( distance > _worst_err) { _worst_err = distance; _worst_row = cs[0].start()+row; }
      }
      // Scale back down to local mean
      for( int clu = 0; clu < _k; clu++ )
        if( _size[clu] != 0 ) ArrayUtils.div(_cMeans[clu], _size[clu]);
      _centers = null;
      _means = _mults = null;
      _modes = null;
    }

    @Override public void reduce(IterationTask mr) {
      _reassigned_count += mr._reassigned_count;
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
      for( int clu=0; clu< _k; clu++ ) {
        for( int col=0; col<_lo[clu].length; col++ ) {
          _lo[clu][col] = Math.min(mr._lo[clu][col], _lo[clu][col]);
          _hi[clu][col] = Math.max(mr._hi[clu][col], _hi[clu][col]);
        }
      }
      // track global worst-row
      if( _worst_err < mr._worst_err) { _worst_err = mr._worst_err; _worst_row = mr._worst_row; }
    }
  }

  // A pair result: nearest cluster center and the square distance
  private static final class ClusterDist { int _cluster; double _dist;  }

  private static double minSqr(double[][] centers, double[] point, String[][] isCats, ClusterDist cd) {
    return closest(centers, point, isCats, cd, centers.length)._dist;
  }

  private static double minSqr(double[][] centers, double[] point, String[][] isCats, ClusterDist cd, int count) {
    return closest(centers,point,isCats,cd,count)._dist;
  }

  private static ClusterDist closest(double[][] centers, double[] point, String[][] isCats, ClusterDist cd) {
    return closest(centers, point, isCats, cd, centers.length);
  }

  /** Return both nearest of N cluster center/centroids, and the square-distance. */
  private static ClusterDist closest(double[][] centers, double[] point, String[][] isCats, ClusterDist cd, int count) {
    int min = -1;
    double minSqr = Double.MAX_VALUE;
    for( int cluster = 0; cluster < count; cluster++ ) {
      double sqr = hex.genmodel.GenModel.KMeans_distance(centers[cluster],point,isCats);
      if( sqr < minSqr ) {      // Record nearest cluster
        min = cluster;
        minSqr = sqr;
      }
    }
    cd._cluster = min;          // Record nearest cluster
    cd._dist = minSqr;          // Record square-distance
    return cd;                  // Return for flow-coding
  }

  /** Return square-distance of point to all clusters. */
  private static double[] getDistances(double[][] centers, double[] point, String[][] isCats) {
    double[] distances = new double[centers.length];
    for( int cluster = 0; cluster < centers.length; cluster++ ) {
      distances[cluster] = hex.genmodel.GenModel.KMeans_distance(centers[cluster],point,isCats);
    }
    return distances;
  }

  // KMeans++ re-clustering
  private static double[][] recluster(double[][] points, Random rand, int N, Initialization init, String[][] isCats) {
    double[][] res = new double[N][];
    res[0] = points[0];
    int count = 1;
    ClusterDist cd = new ClusterDist();
    switch( init ) {
      case Random:
        break;
      case PlusPlus: { // k-means++
        while( count < res.length ) {
          double sum = 0;
          for (double[] point1 : points) sum += minSqr(res, point1, isCats, cd, count);

          for (double[] point : points) {
            if (minSqr(res, point, isCats, cd, count) >= rand.nextDouble() * sum) {
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
            double sqr = minSqr(res, points[i], isCats, cd, count);
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

  private void randomRow(Vec[] vecs, Random rand, double[] center, double[] means, double[] mults, int[] modes) {
    long row = Math.max(0, (long) (rand.nextDouble() * vecs[0].length()) - 1);
    data(center, vecs, row, means, mults, modes);
  }

  // Pick most common cat level for each cluster_centers' cat columns
  private static double[][] max_cats(double[][] centers, long[][][] cats, String[][] isCats) {
    for( int clu = 0; clu < centers.length; clu++ )
      for( int col = 0; col < centers[0].length; col++ )
        if( isCats[col] != null )
          centers[clu][col] = ArrayUtils.maxIndex(cats[clu][col]);
    return centers;
  }

  private static double[][] destandardize(double[][] centers, String[][] isCats, double[] means, double[] mults) {
    int K = centers.length;
    int N = centers[0].length;
    double[][] value = new double[K][N];
    for( int clu = 0; clu < K; clu++ ) {
      System.arraycopy(centers[clu],0,value[clu],0,N);
      if( mults!=null ) {        // Reverse standardization
        for( int col = 0; col < N; col++)
          if( isCats[col] == null )
            value[clu][col] = value[clu][col] / mults[col] + means[col];
      }
    }
    return value;
  }

  private static void data(double[] values, Vec[] vecs, long row, double[] means, double[] mults, int[] modes) {
    for( int i = 0; i < values.length; i++ ) {
      values[i] = Kmeans_preprocessData(vecs[i].at(row), i, means, mults, modes);
    }
  }

  private static void data(double[] values, Chunk[] chks, int row, double[] means, double[] mults, int[] modes) {
    for( int i = 0; i < values.length; i++ ) {
      values[i] = Kmeans_preprocessData(chks[i].atd(row), i, means, mults, modes);
    }
  }


  /**
   * This helper creates a ModelMetricsClustering from a trained model
   * @param model, must contain valid statistics from training, such as _betweenss etc.
   */
  private ModelMetricsClustering makeTrainingMetrics(KMeansModel model) {
    ModelMetricsClustering mm = new ModelMetricsClustering(model, train(), CustomMetric.EMPTY);
    mm._size = model._output._size;
    mm._withinss = model._output._withinss;
    mm._betweenss = model._output._betweenss;
    mm._totss = model._output._totss;
    mm._tot_withinss = model._output._tot_withinss;
    model.addMetrics(mm);
    return mm;
  }

  private static class SplitTask extends MRTask<SplitTask> {
    // IN
    double[][] _centers;
    double[] _means, _mults;      // Standardization
    int[] _modes;   // Imputation of missing categoricals
    final int _k;
    final String[][] _isCats;
    final boolean _hasWeight;
    final int _clusterToSplit;
    final int _dimToSplit;
    final double _splitPoint;

    // OUT
    double[][] _cMeans;         // Means for each cluster
    long[] _size;               // Number of rows in each cluster

    SplitTask(double[][] centers, double[] means, double[] mults, int[] modes, String[][] isCats, int k, boolean hasWeight, int clusterToSplit, int dimToSplit, double splitPoint) {
      _centers = centers;
      _means = means;
      _mults = mults;
      _modes = modes;
      _isCats = isCats;
      _k = k;
      _hasWeight = hasWeight;
      _clusterToSplit = clusterToSplit;
      _dimToSplit = dimToSplit;
      _splitPoint = splitPoint;
    }

    @Override public void map(Chunk[] cs) {
      int N = cs.length - (_hasWeight ? 1:0) - 1 /*clusterassignment*/;
      assert _centers[0].length==N;
      _cMeans = new double[_k][N];
      _size = new long[_k];

      Chunk assignment = cs[cs.length-1];
      // Find closest cluster center for each row
      double[] values = new double[N]; // Temp data to hold row as doubles
      ClusterDist cd = new ClusterDist();
      for( int row = 0; row < cs[0]._len; row++ ) {
        if (assignment.at8(row) != _clusterToSplit) continue;

        double weight = _hasWeight ? cs[N].atd(row) : 1;
        if (weight == 0) continue; //skip holdout rows

        assert(weight == 1); //K-Means only works for weight 1 (or weight 0 for holdout)

        data(values, cs, row, _means, _mults, _modes); // Load row as doubles
        assert (_isCats[_dimToSplit]==null);
        if (values[_dimToSplit] > _centers[_clusterToSplit][_dimToSplit]) {
          cd._cluster = _centers.length-1;
          assignment.set(row, cd._cluster);
        } else {
          cd._cluster = _clusterToSplit;
        }

        int clu = cd._cluster;
        assert clu != -1;       // No broken rows

        // Add values and increment counter for chosen cluster
        for( int col = 0; col < N; col++ )
          _cMeans[clu][col] += values[col]; // Sum the column centers
        _size[clu]++;
      }
      // Scale back down to local mean
      for( int clu = 0; clu < _k; clu++ )
        if( _size[clu] != 0 ) ArrayUtils.div(_cMeans[clu], _size[clu]);
      _centers = null;
      _means = _mults = null;
      _modes = null;
    }

    @Override public void reduce(SplitTask mr) {
      for( int clu = 0; clu < _k; clu++ ) {
        long ra =    _size[clu];
        long rb = mr._size[clu];
        double[] ma =    _cMeans[clu];
        double[] mb = mr._cMeans[clu];
        for( int c = 0; c < ma.length; c++ ) // Recursive mean
          if( ra+rb > 0 ) ma[c] = (ma[c] * ra + mb[c] * rb) / (ra + rb);
      }
      ArrayUtils.add(_size, mr._size);
    }
  }
}


