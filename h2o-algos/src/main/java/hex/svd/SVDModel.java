package hex.svd;

import hex.DataInfo;
import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.*;
import water.codegen.CodeGeneratorPipeline;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.JCodeGen;
import water.util.SBPrintStream;

public class SVDModel extends Model<SVDModel,SVDModel.SVDParameters,SVDModel.SVDOutput> {
  public static class SVDParameters extends Model.Parameters {
    public String algoName() { return "SVD"; }
    public String fullName() { return "Singular Value Decomposition"; }
    public String javaName() { return SVDModel.class.getName(); }
    @Override public long progressUnits() {
      switch(_svd_method) {
        case GramSVD:    return 2;
        case Power:      return 1 + _nv;
        case Randomized: return 5 + _max_iterations;
        default:         return _nv;
      }
    }
    public DataInfo.TransformType _transform = DataInfo.TransformType.NONE; // Data transformation (demean to compare with PCA)
    public Method _svd_method = Method.GramSVD;   // Method for computing SVD
    public int _nv = 1;    // Number of right singular vectors to calculate
    public int _max_iterations = 1000;    // Maximum number of iterations
    public long _seed = System.nanoTime();        // RNG seed
    // public Key<Frame> _u_key;         // Frame key for left singular vectors (U)
    public String _u_name;
    // public Key<Frame> _v_key;        // Frame key for right singular vectors (V)
    public String _v_name;
    public boolean _keep_u = true;    // Should left singular vectors be saved in memory? (Only applies if _only_v = false)
    public boolean _save_v_frame = true;   // Should right singular vectors be saved as a frame?
    public boolean _only_v = false;   // For power method (others ignore): Compute only right singular vectors? (Faster if true)
    public boolean _use_all_factor_levels = true;   // When expanding categoricals, should first level be dropped?
    public boolean _impute_missing = false;   // Should missing numeric values be imputed with the column mean?

    public enum Method {
      GramSVD, Power, Randomized
    }
    @Override protected long nFoldSeed() { return _seed; }
  }

  public static class SVDOutput extends Model.Output {
    // Iterations executed (Power and Randomized methods only)
    public int _iterations;

    // Right singular vectors (V)
    public double[][] _v;     // Used internally for PCA and GLRM
    public Key<Frame> _v_key;

    // Singular values (diagonal of D)
    public double[] _d;

    // Frame key for left singular vectors (U)
    public Key<Frame> _u_key;

    // Number of categorical and numeric columns
    public int _ncats;
    public int _nnums;

    // Number of good rows in training frame (not skipped)
    public long _nobs;

    // Total column variance for expanded and transformed data
    public double _total_variance;

    // Categorical offset vector
    public int[] _catOffsets;

    // If standardized, mean of each numeric data column
    public double[] _normSub;

    // If standardized, one over standard deviation of each numeric data column
    public double[] _normMul;

    // Permutation matrix mapping training col indices to adaptedFrame
    public int[] _permutation;

    // Expanded column names of adapted training frame
    public String[] _names_expanded;

    public SVDOutput(SVD b) { super(b); }

    @Override public ModelCategory getModelCategory() { return ModelCategory.DimReduction; }
  }

  public SVDModel(Key selfKey, SVDParameters parms, SVDOutput output) { super(selfKey,parms,output); }

  @Override protected Futures remove_impl( Futures fs ) {
    if (null != _output._u_key)
      _output._u_key.remove(fs);
    if (null != _output._v_key)
      _output._v_key.remove(fs);
    return super.remove_impl(fs);
  }

  /** Write out K/V pairs */
  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) { 
    ab.putKey(_output._u_key);
    ab.putKey(_output._v_key);
    return super.writeAll_impl(ab);
  }
  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) { 
    ab.getKey(_output._u_key,fs);
    ab.getKey(_output._v_key,fs);
    return super.readAll_impl(ab,fs);
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new ModelMetricsSVD.SVDModelMetrics(_parms._nv);
  }

  public static class ModelMetricsSVD extends ModelMetricsUnsupervised {
    public ModelMetricsSVD(Model model, Frame frame) {
      super(model, frame, 0, Double.NaN);
    }

    // SVD currently does not have any model metrics to compute during scoring
    public static class SVDModelMetrics extends MetricBuilderUnsupervised {
      public SVDModelMetrics(int dims) {
        _work = new double[dims];
      }

      @Override public double[] perRow(double[] preds, float[] dataRow, Model m) { return preds; }

      @Override public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
        return m._output.addModelMetrics(new ModelMetricsSVD(m, f));
      }
    }
  }

  @Override protected Frame predictScoreImpl(Frame orig, Frame adaptedFr, String destination_key, final Job j) {
    Frame adaptFrm = new Frame(adaptedFr);
    for(int i = 0; i < _parms._nv; i++)
      adaptFrm.add("PC"+String.valueOf(i+1),adaptFrm.anyVec().makeZero());

    new MRTask() {
      @Override public void map( Chunk chks[] ) {
        if (isCancelled() || j != null && j.stop_requested()) return;
        double tmp [] = new double[_output._names.length];
        double preds[] = new double[_parms._nv];
        for( int row = 0; row < chks[0]._len; row++) {
          double p[] = score0(chks, row, tmp, preds);
          for( int c=0; c<preds.length; c++ )
            chks[_output._names.length+c].set(row, p[c]);
        }
        if (j !=null) j.update(1);
      }
    }.doAll(adaptFrm);

    // Return the projection into right singular vector (V) space
    int x = _output._names.length, y = adaptFrm.numCols();
    Frame f = adaptFrm.extractFrame(x, y); // this will call vec_impl() and we cannot call the delete() below just yet

    f = new Frame((null == destination_key ? Key.make() : Key.make(destination_key)), f.names(), f.vecs());
    DKV.put(f);
    makeMetricBuilder(null).makeModelMetrics(this, orig, null, null);
    return f;
  }

  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    int numStart = _output._catOffsets[_output._catOffsets.length-1];
    assert data.length == _output._permutation.length;

    for(int i = 0; i < _parms._nv; i++) {
      preds[i] = 0;
      for (int j = 0; j < _output._ncats; j++) {
        double tmp = data[_output._permutation[j]];
        int last_cat = _output._catOffsets[j+1]-_output._catOffsets[j]-1;   // Missing categorical values are mapped to extra (last) factor
        int level = Double.isNaN(tmp) ? last_cat : (int)tmp - (_parms._use_all_factor_levels ? 0:1);  // Reduce index by 1 if first factor level dropped during training
        if (level < 0 || level > last_cat) continue;  // Skip categorical level in test set but not in train
        preds[i] += _output._v[_output._catOffsets[j]+level][i];
      }

      int dcol = _output._ncats;
      int vcol = numStart;
      for (int j = 0; j < _output._nnums; j++) {
        preds[i] += (data[_output._permutation[dcol]] - _output._normSub[j]) * _output._normMul[j] * _output._v[vcol][i];
        dcol++; vcol++;
      }
    }
    return preds;
  }

  @Override protected SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
    sb = super.toJavaInit(sb, fileCtx);
    sb.ip("public boolean isSupervised() { return " + isSupervised() + "; }").nl();
    sb.ip("public int nfeatures() { return "+_output.nfeatures()+"; }").nl();
    sb.ip("public int nclasses() { return "+_parms._nv+"; }").nl();

    if (_output._nnums > 0) {
      JCodeGen.toStaticVar(sb, "NORMMUL", _output._normMul, "Standardization/Normalization scaling factor for numerical variables.");
      JCodeGen.toStaticVar(sb, "NORMSUB", _output._normSub, "Standardization/Normalization offset for numerical variables.");
    }
    JCodeGen.toStaticVar(sb, "CATOFFS", _output._catOffsets, "Categorical column offsets.");
    JCodeGen.toStaticVar(sb, "PERMUTE", _output._permutation, "Permutation index vector.");
    JCodeGen.toStaticVar(sb, "EIGVECS", _output._v, "Eigenvector matrix.");
    return sb;
  }

  @Override protected void toJavaPredictBody(SBPrintStream bodySb,
                                             CodeGeneratorPipeline classCtx,
                                             CodeGeneratorPipeline fileCtx,
                                             final boolean verboseCode) {
    bodySb.i().p("java.util.Arrays.fill(preds,0);").nl();
    final int cats = _output._ncats;
    final int nums = _output._nnums;
    bodySb.i().p("final int nstart = CATOFFS[CATOFFS.length-1];").nl();
    bodySb.i().p("for(int i = 0; i < ").p(_parms._nv).p("; i++) {").nl();
    // Categorical columns
    bodySb.i(1).p("for(int j = 0; j < ").p(cats).p("; j++) {").nl();
    bodySb.i(2).p("double d = data[PERMUTE[j]];").nl();
    bodySb.i(2).p("int last = CATOFFS[j+1]-CATOFFS[j]-1;").nl();
    bodySb.i(2).p("int c = Double.isNaN(d) ? last : (int)d").p(_parms._use_all_factor_levels ? ";":"-1;").nl();
    bodySb.i(2).p("if(c < 0 || c > last) continue;").nl();
    bodySb.i(2).p("preds[i] += EIGVECS[CATOFFS[j]+c][i];").nl();
    bodySb.i(1).p("}").nl();

    // Numeric columns
    bodySb.i(1).p("for(int j = 0; j < ").p(nums).p("; j++) {").nl();
    bodySb.i(2).p("preds[i] += (data[PERMUTE[j" + (cats > 0 ? "+" + cats : "") + "]]-NORMSUB[j])*NORMMUL[j]*EIGVECS[j" + (cats > 0 ? "+ nstart" : "") +"][i];").nl();
    bodySb.i(1).p("}").nl();
    bodySb.i().p("}").nl();
  }
}
