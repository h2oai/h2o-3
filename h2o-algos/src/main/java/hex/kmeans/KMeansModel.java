package hex.kmeans;

import hex.Model;
import water.Key;
import water.api.ModelSchema;
import water.fvec.*;

public class KMeansModel extends Model<KMeansModel,KMeansModel.KMeansParameters,KMeansModel.KMeansOutput> {

  public static class KMeansParameters extends Model.Parameters {
    public int _K;                        // Number of clusters
    public int _max_iters = 1000;         // Max iterations
    public boolean _normalize = true;     // Normalize columns
    public long _seed = System.nanoTime(); // RNG seed
    public KMeans.Initialization _init = KMeans.Initialization.Furthest;
  }

  public static class KMeansOutput extends Model.Output {
    // Number of categorical variables in the training set; they are all moved
    // up-front and use a different distance metric than numerical variables
    public int _ncats;

    // Iterations executed
    public int _iters;

    // Cluster centers.  During model init, might be null or might have a "K"
    // which is oversampled alot.  Not normalized (although if normalization is
    // used during the building process, the *builders* clusters are normalized).
    public double[/*K*/][/*features*/] _clusters;
    // Rows per cluster
    public long[/*K*/] _rows;

    // Sum squared distance between each point and its cluster center, divided by rows
    public double[/*K*/] _mses;   // Per-cluster MSE, variance

    // Sum squared distance between each point and its cluster center, divided by rows.
    public double _mse;           // Total MSE, variance

    public KMeansOutput( KMeans b ) { super(b); }

    /** Override because base class implements ncols-1 for features with the
     *  last column as a response variable; for KMeans all the columns are
     *  features. */
    @Override public int nfeatures() { return _names.length; }

    @Override public ModelCategory getModelCategory() {
      return Model.ModelCategory.Clustering;
    }
  }

  public KMeansModel(Key selfKey, KMeansParameters parms, KMeansOutput output) { super(selfKey,parms,output); }

  @Override
  public boolean isSupervised() {return false;}

  // Default publically visible Schema is V2
  @Override public ModelSchema schema() { return new KMeansModelV2(); }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override protected float[] score0( Chunk chks[], int row_in_chunk, double[] tmp, float[] preds ) {
    assert chks.length>=_output._names.length;
    for( int i=0; i<_output._names.length; i++ )
      tmp[i] = chks[i].at0(row_in_chunk);
    return score0(tmp,preds);
  }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    preds[0] = KMeans.closest(_output._clusters,data,_output._ncats);
    return preds;
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
//        //sb.append("<iframe training_frame=\"" + "/Inspect.html?key=KMeansClusters\"" + "width = \"850\" height = \"550\" marginwidth=\"25\" marginheight=\"25\" scrolling=\"yes\"></iframe>" );
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
//    @API(help = "Total Sum of squares = total_within_SS + between_cluster_SS")
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
}
