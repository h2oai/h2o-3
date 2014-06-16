package hex.kmeans;

//import hex.FrameTask.DataInfo;
//import hex.KMeans.Initialization;
import water.*;
import water.H2O.H2OCountedCompleter;
//import water.api.DocGen;
//import water.api.Progress2;
//import water.api.Request;
//import water.fvec.Chunk;
import water.fvec.Frame;
//import water.fvec.NewChunk;
//import water.fvec.Vec;
//import water.util.RString;
//import water.util.Utils;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Random;

/**
 * Scalable K-Means++ (KMeans||)<br>
 * http://theory.stanford.edu/~sergei/papers/vldb12-kmpar.pdf<br>
 * http://www.youtube.com/watch?v=cigXAxV3XcY
 */
public class KMeans extends Job<KMeansModel> {

//  @API(help = "Cluster initialization: None - chooses initial centers at random; Plus Plus - choose first center at random, subsequent centers chosen from probability distribution weighted so that points further from first center are more likey to be selected; Furthest - chooses initial point at random, subsequent point taken as the point furthest from prior point.", filter = Default.class, json=true)
//  public Initialization initialization = Initialization.None;
//
//  @API(help = "Whether data should be normalized", filter = Default.class, json=true)
//  public boolean normalize;
//
//  @API(help = "Seed for the random number generator", filter = Default.class, json=true)
//  public long seed = new Random().nextLong();
//
//  @API(help = "Drop columns with more than 20% missing values", filter = Default.class)
//  public boolean drop_na_cols = true;

  final Key _src;               // Source frame 
  final int _K;                 // Number of clusters
  final int _max_iter;          // Max iterations to try

  // Called from Nano thread; start the KMeans Job on a F/J thread
  public KMeans( Key src, int K, int max_iter) {
    super(Key.make("KMeansModel"),"K-means",max_iter/*work is clusters*/);
    _src = src;
    _K = K;
    _max_iter = max_iter;
    start(new KMeansDriver());
  }

  // ----------------------
  private class KMeansDriver extends H2OCountedCompleter<KMeansDriver> {

    @Override protected void compute2() {
      Frame fr = null;
      try {
        fr = DKV.get(_src).get();
        fr.read_lock(_key);

//    // Drop ignored cols and, if user asks for it, cols with too many NAs
//    Frame fr = DataInfo.prepareFrame(source, ignored_cols, false, drop_na_cols);
//    String[] names = fr.names();
//    Vec[] vecs = fr.vecs();
//    if(vecs == null || vecs.length == 0)
//      throw new IllegalArgumentException("No columns selected. Check that selected columns have not been dropped due to too many NAs.");
//    DataInfo dinfo = new DataInfo(fr, 0, false, normalize, false);
//
//    // Fill-in response based on K99
//    String[] domain = new String[k];
//    for( int i = 0; i < domain.length; i++ )
//      domain[i] = "Cluster " + i;
//    String[] namesResp = Utils.append(names, "response");
//    String[][] domaiResp = (String[][]) Utils.append((new Frame(names, vecs)).domains(), (Object) domain);
//    KMeans2Model model = new KMeans2Model(this, destination_key, sourceKey, namesResp, domaiResp);
//    model.delete_and_lock(self());
//    model.k = k; model.normalized = normalize; model.max_iter = max_iter;
//
//    // TODO remove when stats are propagated with vecs?
//    double[] means = new double[vecs.length];
//    double[] mults = normalize ? new double[vecs.length] : null;
//    for( int i = 0; i < vecs.length; i++ ) {
//      means[i] = (float) vecs[i].mean();
//      if( mults != null ) {
//        double sigma = vecs[i].sigma();
//        mults[i] = normalize(sigma) ? 1.0 / sigma : 1.0;
//      }
//    }
//
//    // -1 to be different from all chunk indexes (C.f. Sampler)
//    Random rand = Utils.getRNG(seed - 1);
//    double[][] clusters;
//    if( initialization == Initialization.None ) {
//      // Initialize all clusters to random rows
//      clusters = new double[k][vecs.length];
//      for (double[] cluster : clusters)
//        randomRow(vecs, rand, cluster, means, mults);
//    } else {
//      // Initialize first cluster to random row
//      clusters = new double[1][];
//      clusters[0] = new double[vecs.length];
//      randomRow(vecs, rand, clusters[0], means, mults);
//
//      while( model.iterations < 5 ) {
//        // Sum squares distances to clusters
//        SumSqr sqr = new SumSqr();
//        sqr._clusters = clusters;
//        sqr._means = means;
//        sqr._mults = mults;
//        sqr.doAll(vecs);
//
//        // Sample with probability inverse to square distance
//        Sampler sampler = new Sampler();
//        sampler._clusters = clusters;
//        sampler._sqr = sqr._sqr;
//        sampler._probability = k * 3; // Over-sampling
//        sampler._seed = seed;
//        sampler._means = means;
//        sampler._mults = mults;
//        sampler.doAll(vecs);
//        clusters = Utils.append(clusters, sampler._sampled);
//
//        if( !isRunning(self()) )
//          return;
//        model.centers = normalize ? denormalize(clusters, vecs) : clusters;
//        model.total_within_SS = sqr._sqr;
//        model.iterations++;
//        model.update(self());
//      }
//
//      clusters = recluster(clusters, k, rand, initialization);
//    }
//
//    for( ;; ) {
//      Lloyds task = new Lloyds();
//      task._clusters = clusters;
//      task._means = means;
//      task._mults = mults;
//      task._ncats = dinfo._cats;
//      task._nnums = dinfo._nums;
//      task.doAll(vecs);
//
//      model.centers = clusters = normalize ? denormalize(task._cMeans, vecs) : task._cMeans;
//      model.between_cluster_variances = task._betwnSqrs;
//      double[] variances = new double[task._cSqrs.length];
//      for( int clu = 0; clu < task._cSqrs.length; clu++ )
//        for( int col = 0; col < task._cSqrs[clu].length; col++ )
//          variances[clu] += task._cSqrs[clu][col];
//      double between_cluster_SS = 0.0;
//      for (int clu = 0; clu < task._betwnSqrs.length; clu++)
//          between_cluster_SS += task._betwnSqrs[clu];
//      model.between_cluster_SS = between_cluster_SS;
//      model.within_cluster_variances = variances;
//      model.total_within_SS = task._sqr;
//      model.total_SS = model.total_within_SS + model.between_cluster_SS;
//      model.size = task._rows;
//      model.iterations++;
//      model.update(self());
//      if( model.iterations >= max_iter ) {
//        Clusters cc = new Clusters();
//        cc._clusters = clusters;
//        cc._means = means;
//        cc._mults = mults;
//        cc.doAll(1, vecs);
//        Frame fr2 = cc.outputFrame(model._clustersKey,new String[]{"Cluster ID"}, new String[][] { Utils.toStringMap(0,cc._clusters.length-1) } );
//        fr2.delete_and_lock(self()).unlock(self());
//        break;
//      }
//      if( !isRunning(self()) )
//        break;
//    }
//    model.unlock(self());
      } finally {
        if( fr != null ) fr.unlock(_key);
      }
      tryComplete();
      throw H2O.unimpl();
    }
  }

//  public static class KMeans2ModelView extends Request2 {
//    static final int API_WEAVER = 1;
//    static public DocGen.FieldDoc[] DOC_FIELDS;
//
//    @API(help = "KMeans2 Model", json = true, filter = Default.class)
//    public KMeans2Model model;
//
//    public static String link(String txt, Key model) {
//      return "<a href='" + new KMeans2ModelView().href() + ".html?model=" + model + "'>" + txt + "</a>";
//    }
//
//    public static Response redirect(Request req, Key model) {
//      return Response.redirect(req, new KMeans2ModelView().href(), "model", model);
//    }
//
//    @Override protected Response serve() {
//      return Response.done(this);
//    }
//
//    @Override public boolean toHTML(StringBuilder sb) {
//      if( model != null ) {
//        model.parameters.makeJsonBox(sb);
//        DocGen.HTML.section(sb, "Cluster Centers: "); //"Total Within Cluster Sum of Squares: " + model.total_within_SS);
//        table(sb, "Clusters", model._names, model.centers);
//        double[][] rows = new double[model.within_cluster_variances.length][1];
//        for( int i = 0; i < rows.length; i++ )
//          rows[i][0] = model.within_cluster_variances[i];
//        columnHTMLlong(sb, "Cluster Size", model.size);
//        DocGen.HTML.section(sb, "Cluster Variances: ");
//        table(sb, "Clusters", new String[]{"Within Cluster Variances"}, rows);
//        columnHTML(sb, "Between Cluster Variances", model.between_cluster_variances);
//        sb.append("<br />");
//        DocGen.HTML.section(sb, "Overall Totals: ");
//        double[] row = new double[]{model.total_SS, model.total_within_SS, model.between_cluster_SS};
//        rowHTML(sb, new String[]{"Total Sum of Squares", "Total Within Cluster Sum of Squares", "Between Cluster Sum of Squares"}, row);
//        DocGen.HTML.section(sb, "Cluster Assignments by Observation: ");
//        RString rs = new RString("<a href='Inspect2.html?src_key=%$key'>%content</a>");
//        rs.replace("key", model._key + "_clusters");
//        rs.replace("content", "View the row-by-row cluster assignments");
//        sb.append(rs.toString());
//        //sb.append("<iframe src=\"" + "/Inspect.html?key=KMeansClusters\"" + "width = \"850\" height = \"550\" marginwidth=\"25\" marginheight=\"25\" scrolling=\"yes\"></iframe>" );
//        return true;
//      }
//      return false;
//    }
//
//    private static void rowHTML(StringBuilder sb, String[] header, double[] ro) {
//        sb.append("<span style='display: inline-block; '>");
//        sb.append("<table class='table table-striped table-bordered'>");
//        sb.append("<tr>");
//        for (String aHeader : header) sb.append("<th>").append(aHeader).append("</th>");
//        sb.append("</tr>");
//        sb.append("<tr>");
//        for (double row : ro) {
//            sb.append("<td>").append(ElementBuilder.format(row)).append("</td>");
//        }
//        sb.append("</tr>");
//        sb.append("</table></span>");
//    }
//
//    private static void columnHTML(StringBuilder sb, String name, double[] rows) {
//        sb.append("<span style='display: inline-block; '>");
//        sb.append("<table class='table table-striped table-bordered'>");
//        sb.append("<tr>");
//        sb.append("<th>").append(name).append("</th>");
//        sb.append("</tr>");
//        sb.append("<tr>");
//        for (double row : rows) {
//            sb.append("<tr>");
//            sb.append("<td>").append(ElementBuilder.format(row)).append("</td>");
//            sb.append("</tr>");
//        }
//        sb.append("</table></span>");
//    }
//
//    private static void columnHTMLlong(StringBuilder sb, String name, long[] rows) {
//      sb.append("<span style='display: inline-block; '>");
//      sb.append("<table class='table table-striped table-bordered'>");
//      sb.append("<tr>");
//      sb.append("<th>").append(name).append("</th>");
//      sb.append("</tr>");
//      sb.append("<tr>");
//      for (double row : rows) {
//         sb.append("<tr>");
//         sb.append("<td>").append(ElementBuilder.format(row)).append("</td>");
//         sb.append("</tr>");
//      }
//      sb.append("</table></span>");
//    }
//
//    private static void table(StringBuilder sb, String title, String[] names, double[][] rows) {
//      sb.append("<span style='display: inline-block;'>");
//      sb.append("<table class='table table-striped table-bordered'>");
//      sb.append("<tr>");
//      sb.append("<th>").append(title).append("</th>");
//      for( int i = 0; names != null && i < rows[0].length; i++ )
//        sb.append("<th>").append(names[i]).append("</th>");
//      sb.append("</tr>");
//      for( int r = 0; r < rows.length; r++ ) {
//        sb.append("<tr>");
//        sb.append("<td>").append(r).append("</td>");
//        for( int c = 0; c < rows[r].length; c++ )
//          sb.append("<td>").append(ElementBuilder.format(rows[r][c])).append("</td>");
//        sb.append("</tr>");
//      }
//      sb.append("</table></span>");
//    }
//  }
//
//  public static class KMeans2Model extends Model implements Progress {
//    static final int API_WEAVER = 1;
//    static public DocGen.FieldDoc[] DOC_FIELDS;
//
//    @API(help = "Model parameters")
//    private final KMeans2 parameters;    // This is used purely for printing values out.
//
//    @API(help = "Cluster centers, always denormalized")
//    public double[][] centers;
//
//    @API(help = "Sum of within cluster sum of squares")
//    public double total_within_SS;
//
//    @API(help = "Between cluster sum of square distances")
//    public double between_cluster_SS;
//
//    @API(help = "Total Sum of squares = total_within_SS + betwen_cluster_SS")
//    public double total_SS;
//
//    @API(help = "Number of clusters")
//    public int k;
//
//    @API(help = "Numbers of observations in each cluster.")
//    public long[] size;
//
//    @API(help = "Whether data was normalized")
//    public boolean normalized;
//
//    @API(help = "Maximum number of iterations before stopping")
//    public int max_iter = 100;
//
//    @API(help = "Iterations the algorithm ran")
//    public int iterations;
//
//    @API(help = "Within cluster sum of squares per cluster")
//    public double[] within_cluster_variances;
//
//    @API(help = "Between Cluster square distances per cluster")
//    public double[] between_cluster_variances;
//
//    @API(help = "The row-by-row cluster assignments")
//    public final Key _clustersKey;
//
//    // Normalization caches
//    private transient double[][] _normClust;
//    private transient double[] _means, _mults;
//    private transient int _ncats, _nnums;
//
//    public KMeans2Model(KMeans2 params, Key selfKey, Key dataKey, String names[], String domains[][]) {
//      super(selfKey, dataKey, names, domains);
//      parameters = params;
//      _clustersKey = Key.make(selfKey.toString() + "_clusters");
//    }
//
//    @Override public double mse() { return total_within_SS; }
//
//    @Override public float progress() {
//      return Math.min(1f, iterations / (float) max_iter);
//    }
//
//    @Override protected float[] score0(Chunk[] chunks, int rowInChunk, double[] tmp, float[] preds) {
//      // If only one cluster, then everything is trivially assigned to it
//      if(preds.length == 1) {
//        preds[0] = 0;
//        return preds;
//      }
//      double[][] cs = centers;
//      int numInputCols = tmp.length-1; // -1 as there is no response column here
//      if( normalized && _normClust == null )
//        cs = _normClust = normalize(centers, chunks);
//      if( _means == null ) {
//        _means = new double[numInputCols];
//        for( int i = 0; i < numInputCols; i++ )
//          _means[i] = chunks[i]._vec.mean();
//      }
//      if( normalized && _mults == null ) {
//        _mults = new double[numInputCols];
//        for( int i = 0; i < numInputCols; i++ ) {
//          double sigma = chunks[i]._vec.sigma();
//          _mults[i] = normalize(sigma) ? 1 / sigma : 1;
//        }
//      }
//      data(tmp, chunks, rowInChunk, _means, _mults);
//      Arrays.fill(preds, 0);
//      // int cluster = closest(cs, tmp, new ClusterDist())._cluster;
//      int cluster = closest(cs, tmp, _ncats, new ClusterDist())._cluster;
//      preds[0] = cluster;       // prediction in preds[0]
//      preds[1+cluster] = 1;     // class distribution
//      return preds;
//    }
//
//    @Override protected float[] score0(double[] data, float[] preds) {
//      throw new UnsupportedOperationException();
//    }
//
//    /** Remove any Model internal Keys */
//    @Override public Futures delete_impl(Futures fs) {
//      Lockable.delete(_clustersKey);
//      return fs;
//    }
//  }
//
//  public class Clusters extends MRTask2<Clusters> {
//      // IN
//      double[][] _clusters;         // Cluster centers
//      double[] _means, _mults;      // Normalization
//      int _ncats, _nnums;
//
//      @Override public void map(Chunk[] cs, NewChunk ncs) {
//          double[] values = new double[_clusters[0].length];
//          ClusterDist cd = new ClusterDist();
//          for (int row = 0; row < cs[0]._len; row++) {
//              data(values, cs, row, _means, _mults);
//              // closest(_clusters, values, cd);
//              closest(_clusters, values, _ncats, cd);
//              int clu = cd._cluster;
//              // ncs[0].addNum(clu);
//              ncs.addEnum(clu);
//          }
//      }
//  }
//
//  public static class SumSqr extends MRTask2<SumSqr> {
//    // IN
//    double[] _means, _mults; // Normalization
//    double[][] _clusters;
//
//    // OUT
//    double _sqr;
//
//    @Override public void map(Chunk[] cs) {
//      double[] values = new double[cs.length];
//      ClusterDist cd = new ClusterDist();
//      for( int row = 0; row < cs[0]._len; row++ ) {
//        data(values, cs, row, _means, _mults);
//        _sqr += minSqr(_clusters, values, cd);
//      }
//      _means = _mults = null;
//      _clusters = null;
//    }
//
//    @Override public void reduce(SumSqr other) {
//      _sqr += other._sqr;
//    }
//  }
//
//  public static class Sampler extends MRTask2<Sampler> {
//    // IN
//    double[][] _clusters;
//    double _sqr;           // Min-square-error
//    double _probability;   // Odds to select this point
//    long _seed;
//    double[] _means, _mults; // Normalization
//
//    // OUT
//    double[][] _sampled;   // New clusters
//
//    @Override public void map(Chunk[] cs) {
//      double[] values = new double[cs.length];
//      ArrayList<double[]> list = new ArrayList<double[]>();
//      Random rand = Utils.getRNG(_seed + cs[0]._start);
//      ClusterDist cd = new ClusterDist();
//
//      for( int row = 0; row < cs[0]._len; row++ ) {
//        data(values, cs, row, _means, _mults);
//        double sqr = minSqr(_clusters, values, cd);
//        if( _probability * sqr > rand.nextDouble() * _sqr )
//          list.add(values.clone());
//      }
//
//      _sampled = new double[list.size()][];
//      list.toArray(_sampled);
//      _clusters = null;
//      _means = _mults = null;
//    }
//
//    @Override public void reduce(Sampler other) {
//      _sampled = Utils.append(_sampled, other._sampled);
//    }
//  }
//
//  public static class Lloyds extends MRTask2<Lloyds> {
//    // IN
//    double[][] _clusters;
//    double[] _means, _mults;      // Normalization
//    int _ncats, _nnums;
//
//    // OUT
//    double[][] _cMeans, _cSqrs; // Means and sum of squares for each cluster
//    double[] _betwnSqrs;        // Between cluster squares
//    double[] _gm;               // Grand Mean (mean of means)
//    long[] _rows;               // Rows per cluster
//    double _sqr;                // Total sqr distance
//
//    @Override public void map(Chunk[] cs) {
//      _cMeans = new double[_clusters.length][_clusters[0].length];
//      _cSqrs = new double[_clusters.length][_clusters[0].length];
//      _betwnSqrs = new double[_clusters.length];
//      _rows = new long[_clusters.length];
//      _gm = new double[_clusters[0].length];
//
//      // Find closest cluster for each row
//      double[] values = new double[_clusters[0].length];
//      ClusterDist cd = new ClusterDist();
//      int[] clusters = new int[cs[0]._len];
//      for( int row = 0; row < cs[0]._len; row++ ) {
//        data(values, cs, row, _means, _mults);
//        // closest(_clusters, values, cd);
//        closest(_clusters, values, _ncats, cd);
//        int clu = clusters[row] = cd._cluster;
//        _sqr += cd._dist;
//        if( clu == -1 )
//          continue; // Ignore broken row
//
//        // Add values and increment counter for chosen cluster
//        for( int col = 0; col < values.length; col++ )
//          _cMeans[clu][col] += values[col];
//        _rows[clu]++;
//      }
//      int[] validMeans = new int[_gm.length];
//      for( int clu = 0; clu < _cMeans.length; clu++ )
//        for( int col = 0; col < _cMeans[clu].length; col++ ) {
//          if(_rows[clu] != 0) {
//            _cMeans[clu][col] /= _rows[clu];
//            _gm[col] += _cMeans[clu][col];
//            validMeans[col]++;
//          }
//        }
//      for (int col = 0; col < _gm.length; col++)
//        if(validMeans[col] != 0)
//          _gm[col] /= validMeans[col];
//
//      for (int clu = 0; clu < _cMeans.length; clu++)
//          for (int col = 0; col < _gm.length; col++) {
//              double mean_delta = _cMeans[clu][col] - _gm[col];
//              _betwnSqrs[clu] += _rows[clu] * mean_delta * mean_delta;
//          }
//      // Second pass for in-cluster variances
//      for( int row = 0; row < cs[0]._len; row++ ) {
//        int clu = clusters[row];
//        if( clu == -1 )
//          continue;
//        data(values, cs, row, _means, _mults);
//        for( int col = 0; col < values.length; col++ ) {
//          double delta = values[col] - _cMeans[clu][col];
//          _cSqrs[clu][col] += delta * delta;
//        }
//      }
//      _clusters = null;
//      _means = _mults = null;
//    }
//
//    @Override public void reduce(Lloyds mr) {
//      for( int clu = 0; clu < _cMeans.length; clu++ )
//        Layer.Stats.reduce(_cMeans[clu], _cSqrs[clu], _rows[clu], mr._cMeans[clu], mr._cSqrs[clu], mr._rows[clu]);
//      Utils.add(_rows, mr._rows);
//      _sqr += mr._sqr;
//    }
//  }
//
//  private static final class ClusterDist {
//    int _cluster;
//    double _dist;
//  }
//
//  private static double minSqr(double[][] clusters, double[] point, ClusterDist cd) {
//    return closest(clusters, point, cd, clusters.length)._dist;
//  }
//
//  private static double minSqr(double[][] clusters, double[] point, ClusterDist cd, int count) {
//    return closest(clusters, point, cd, count)._dist;
//  }
//
//  private static ClusterDist closest(double[][] clusters, double[] point, ClusterDist cd) {
//    return closest(clusters, point, cd, clusters.length);
//  }
//
//  private static ClusterDist closest(double[][] clusters, double[] point, int ncats, ClusterDist cd) {
//    return closest(clusters, point, ncats, cd, clusters.length);
//  }
//
//  private static ClusterDist closest(double[][] clusters, double[] point, ClusterDist cd, int count) {
//    return closest(clusters, point, 0, cd, count);
//  }
//
//  private static ClusterDist closest(double[][] clusters, double[] point, int ncats, ClusterDist cd, int count) {
//    return closest(clusters, point, ncats, cd, count, 1);
//  }
//
//  /** Return both nearest of N cluster/centroids, and the square-distance. */
//  private static ClusterDist closest(double[][] clusters, double[] point, int ncats, ClusterDist cd, int count, double dist) {
//    int min = -1;
//    double minSqr = Double.MAX_VALUE;
//    for( int cluster = 0; cluster < count; cluster++ ) {
//      double sqr = 0;           // Sum of dimensional distances
//      int pts = point.length;   // Count of valid points
//
//      // Expand categoricals into binary indicator cols
//      for(int column = 0; column < ncats; column++) {
//        double d = point[column];
//        if(Double.isNaN(d))
//          pts--;
//        else {
//          // TODO: What is the distance between unequal categoricals?
//          if(d != clusters[cluster][column])
//             sqr += 2 * dist * dist;
//        }
//      }
//
//      for( int column = ncats; column < clusters[cluster].length; column++ ) {
//        double d = point[column];
//        if( Double.isNaN(d) ) { // Bad data?
//          pts--;                // Do not count
//        } else {
//          double delta = d - clusters[cluster][column];
//          sqr += delta * delta;
//        }
//      }
//      // Scale distance by ratio of valid dimensions to all dimensions - since
//      // we did not add any error term for the missing point, the sum of errors
//      // is small - ratio up "as if" the missing error term is equal to the
//      // average of other error terms.  Same math another way:
//      //   double avg_dist = sqr / pts; // average distance per feature/column/dimension
//      //   sqr = sqr * point.length;    // Total dist is average*#dimensions
//      if( 0 < pts && pts < point.length )
//        sqr *= point.length / pts;
//      if( sqr < minSqr ) {
//        min = cluster;
//        minSqr = sqr;
//      }
//    }
//    cd._cluster = min;          // Record nearest cluster
//    cd._dist = minSqr;          // Record square-distance
//    return cd;                  // Return for flow-coding
//  }
//
//  // KMeans++ re-clustering
//  public static double[][] recluster(double[][] points, int k, Random rand, Initialization init) {
//    double[][] res = new double[k][];
//    res[0] = points[0];
//    int count = 1;
//    ClusterDist cd = new ClusterDist();
//    switch( init ) {
//        case None:
//            break;
//        case PlusPlus: { // k-means++
//        while( count < res.length ) {
//          double sum = 0;
//            for (double[] point1 : points) sum += minSqr(res, point1, cd, count);
//
//            for (double[] point : points) {
//                if (minSqr(res, point, cd, count) >= rand.nextDouble() * sum) {
//                    res[count++] = point;
//                    break;
//                }
//            }
//        }
//        break;
//      }
//      case Furthest: { // Takes cluster further from any already chosen ones
//        while( count < res.length ) {
//          double max = 0;
//          int index = 0;
//          for( int i = 0; i < points.length; i++ ) {
//            double sqr = minSqr(res, points[i], cd, count);
//            if( sqr > max ) {
//              max = sqr;
//              index = i;
//            }
//          }
//          res[count++] = points[index];
//        }
//        break;
//      }
//      default:
//        throw new IllegalStateException();
//    }
//    return res;
//  }
//
//  private void randomRow(Vec[] vecs, Random rand, double[] cluster, double[] means, double[] mults) {
//    long row = Math.max(0, (long) (rand.nextDouble() * vecs[0].length()) - 1);
//    data(cluster, vecs, row, means, mults);
//  }
//
//  private static boolean normalize(double sigma) {
//    // TODO unify handling of constant columns
//    return sigma > 1e-6;
//  }
//
//  private static double[][] normalize(double[][] clusters, Chunk[] chks) {
//    double[][] value = new double[clusters.length][clusters[0].length];
//    for( int row = 0; row < value.length; row++ ) {
//      for( int col = 0; col < clusters[row].length; col++ ) {
//        double d = clusters[row][col];
//        Vec vec = chks[col]._vec;
//        d -= vec.mean();
//        d /= normalize(vec.sigma()) ? vec.sigma() : 1;
//        value[row][col] = d;
//      }
//    }
//    return value;
//  }
//
//  private static double[][] denormalize(double[][] clusters, Vec[] vecs) {
//    double[][] value = new double[clusters.length][clusters[0].length];
//    for( int row = 0; row < value.length; row++ ) {
//      for( int col = 0; col < clusters[row].length; col++ ) {
//        double d = clusters[row][col];
//        d *= vecs[col].sigma();
//        d += vecs[col].mean();
//        value[row][col] = d;
//      }
//    }
//    return value;
//  }
//
//  private static void data(double[] values, Vec[] vecs, long row, double[] means, double[] mults) {
//    for( int i = 0; i < values.length; i++ ) {
//      double d = vecs[i].at(row);
//      // values[i] = data(d, i, means, mults);
//      values[i] = data(d, i, means, mults, vecs[i].cardinality());
//    }
//  }
//
//  private static void data(double[] values, Chunk[] chks, int row, double[] means, double[] mults) {
//    for( int i = 0; i < values.length; i++ ) {
//      double d = chks[i].at0(row);
//      // values[i] = data(d, i, means, mults);
//      values[i] = data(d, i, means, mults, chks[i]._vec.cardinality());
//    }
//  }
//
//  /**
//   * Takes mean if NaN, normalize if requested.
//   */
//  private static double data(double d, int i, double[] means, double[] mults) {
//    if( Double.isNaN(d) )
//      d = means[i];
//    if( mults != null ) {
//      d -= means[i];
//      d *= mults[i];
//    }
//    return d;
//  }
//
//  private static double data(double d, int i, double[] means, double[] mults, int cardinality) {
//    if(cardinality == -1) {
//      if( Double.isNaN(d) )
//        d = means[i];
//      if( mults != null ) {
//        d -= means[i];
//        d *= mults[i];
//      }
//    } else {
//      // TODO: If NaN, then replace with majority class?
//      if(Double.isNaN(d))
//        d = Math.min(Math.round(means[i]), cardinality-1);
//    }
//    return d;
//  }
}
