package hex.kmeans;

import hex.ClusteringModel;
import hex.ModelMetrics;
import hex.ModelMetricsClustering;
import water.DKV;
import water.Job;
import water.Key;
import water.MRTask;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.JCodeSB;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.JCodeGen;
import water.util.SBPrintStream;

public class KMeansModel extends ClusteringModel<KMeansModel,KMeansModel.KMeansParameters,KMeansModel.KMeansOutput> {

  public static class KMeansParameters extends ClusteringModel.ClusteringParameters {
    public String algoName() { return "KMeans"; }
    public String fullName() { return "K-means"; }
    public String javaName() { return KMeansModel.class.getName(); }
    @Override public long progressUnits() { return _max_iterations; }
    public int _max_iterations = 1000;     // Max iterations
    public boolean _standardize = true;    // Standardize columns
    public KMeans.Initialization _init = KMeans.Initialization.Furthest;
    public Key<Frame> _user_points;
    public boolean _pred_indicator = false;   // For internal use only: generate indicator cols during prediction
                                              // Ex: k = 4, cluster = 3 -> [0, 0, 1, 0]
  }

  public static class KMeansOutput extends ClusteringModel.ClusteringOutput {
    // Iterations executed
    public int _iterations;

    // Compute average change in standardized cluster centers
    public double[/*iterations*/] _avg_centroids_chg = new double[]{Double.NaN};

    // Sum squared distance between each point and its cluster center.
    public double[/*k*/] _withinss;   // Within-cluster sum of square error

    // Sum squared distance between each point and its cluster center.
    public double _tot_withinss;      // Within-cluster sum-of-square error
    public double[/*iterations*/] _history_withinss = new double[0];

    // Sum squared distance between each point and grand mean.
    public double _totss;            // Total sum-of-square error to grand mean centroid

    // Sum squared distance between each cluster center and grand mean, divided by total number of observations.
    public double _betweenss;    // Total between-cluster sum-of-square error (totss - tot_withinss)

    // Number of categorical columns trained on
    public int _categorical_column_count;

    // Training time
    public long[/*iterations*/] _training_time_ms = new long[]{System.currentTimeMillis()};

    public KMeansOutput( KMeans b ) { super(b); }
  }

  public KMeansModel(Key selfKey, KMeansParameters parms, KMeansOutput output) { super(selfKey,parms,output); }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    assert domain == null;
    return new ModelMetricsClustering.MetricBuilderClustering(_output.nfeatures(),_parms._k);
  }

  @Override protected Frame predictScoreImpl(Frame orig, Frame adaptedFr, String destination_key, final Job j) {
    if (!_parms._pred_indicator) {
      return super.predictScoreImpl(orig, adaptedFr, destination_key, j);
    } else {
      final int len = _parms._k;
      String prefix = "cluster_";
      Frame adaptFrm = new Frame(adaptedFr);
      for(int c = 0; c < len; c++)
        adaptFrm.add(prefix + Double.toString(c+1), adaptFrm.anyVec().makeZero());
      new MRTask() {
        @Override public void map( Chunk chks[] ) {
          if (isCancelled() || j != null && j.stop_requested()) return;
          double tmp [] = new double[_output._names.length];
          double preds[] = new double[len];
          for(int row = 0; row < chks[0]._len; row++) {
            double p[] = score_indicator(chks, row, tmp, preds);
            for(int c = 0; c < preds.length; c++)
              chks[_output._names.length + c].set(row, p[c]);
          }
          if (j != null) j.update(1);
        }
      }.doAll(adaptFrm);

      // Return the predicted columns
      int x = _output._names.length, y = adaptFrm.numCols();
      Frame f = adaptFrm.extractFrame(x, y); // this will call vec_impl() and we cannot call the delete() below just yet

      f = new Frame(Key.<Frame>make(destination_key), f.names(), f.vecs());
      DKV.put(f);
      makeMetricBuilder(null).makeModelMetrics(this, orig, null, null);
      return f;
    }
  }

  public double[] score_indicator(Chunk[] chks, int row_in_chunk, double[] tmp, double[] preds) {
    assert _parms._pred_indicator;
    assert tmp.length == _output._names.length && preds.length == _parms._k;
    for(int i = 0; i < tmp.length; i++)
      tmp[i] = chks[i].atd(row_in_chunk);

    double[] clus = new double[1];
    score0(tmp, clus);   // this saves cluster number into clus[0]

    assert preds != null && ArrayUtils.l2norm2(preds) == 0 : "preds must be a vector of all zeros";
    assert clus[0] >= 0 && clus[0] < preds.length : "Cluster number must be an integer in [0," + String.valueOf(preds.length) + ")";
    preds[(int)clus[0]] = 1;
    return preds;
  }

  public double[] score_ratio(Chunk[] chks, int row_in_chunk, double[] tmp) {
    assert _parms._pred_indicator;
    assert tmp.length == _output._names.length;
    for(int i = 0; i < tmp.length; i++)
      tmp[i] = chks[i].atd(row_in_chunk);

    double[][] centers = _parms._standardize ? _output._centers_std_raw : _output._centers_raw;
    double[] preds = hex.genmodel.GenModel.KMeans_simplex(centers,tmp,_output._domains,_output._normSub,_output._normMul);
    assert preds.length == _parms._k;
    assert Math.abs(ArrayUtils.sum(preds) - 1) < 1e-6 : "Sum of k-means distance ratios should equal 1";
    return preds;
  }

  @Override
  protected double[] score0(double[] data, double[] preds, double weight, double offset) {
    if (weight == 0) return data; //0 distance from itself - validation holdout points don't increase metrics
    assert(weight == 1);
    return score0(data, preds);
  }

  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    double[][] centers = _parms._standardize ? _output._centers_std_raw : _output._centers_raw;
    preds[0] = hex.genmodel.GenModel.KMeans_closest(centers,data,_output._domains,_output._normSub,_output._normMul);
    return preds;
  }

  // Override in subclasses to provide some top-level model-specific goodness
  @Override protected void toJavaPredictBody(SBPrintStream body,
                                             CodeGeneratorPipeline classCtx,
                                             CodeGeneratorPipeline fileCtx,
                                             final boolean verboseCode) {
    // This is model name
    final String mname = JCodeGen.toJavaId(_key.toString());

    if(_parms._standardize) {
      fileCtx.add(new CodeGenerator() {
        @Override
        public void generate(JCodeSB out) {
          JCodeGen.toClassWithArray(out, null, mname + "_MEANS", _output._normSub,
                                    "Column means of training data");
          JCodeGen.toClassWithArray(out, null, mname + "_MULTS", _output._normMul,
                                    "Reciprocal of column standard deviations of training data");
          JCodeGen.toClassWithArray(out, null, mname + "_CENTERS", _output._centers_std_raw,
                                    "Normalized cluster centers[K][features]");
        }
      });

      // Predict function body: main work function is a utility in GenModel class.
      body.ip("preds[0] = KMeans_closest(")
          .pj(mname + "_CENTERS", "VALUES")
          .p(", data, DOMAINS, ")
          .pj(mname + "_MEANS", "VALUES").p(", ")
          .pj(mname + "_MULTS", "VALUES").p(");").nl(); // at function level
    } else {
      fileCtx.add(new CodeGenerator() {
        @Override
        public void generate(JCodeSB out) {
          JCodeGen.toClassWithArray(out, null, mname + "_CENTERS", _output._centers_raw,
                                    "Denormalized cluster centers[K][features]");
        }
      });

      // Predict function body: main work function is a utility in GenModel class.
      body.ip("preds[0] = KMeans_closest(")
          .pj(mname + "_CENTERS", "VALUES")
          .p(",data, DOMAINS, null, null);").nl(); // at function level
    }
  }

  @Override
  protected boolean toJavaCheckTooBig() {
    return _parms._standardize ?
            _output._centers_std_raw.length * _output._centers_std_raw[0].length > 1e6 :
            _output._centers_raw.length * _output._centers_raw[0].length > 1e6;
  }
}
