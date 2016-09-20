package hex.pca;

import hex.DataInfo;
import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import water.*;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.JCodeSB;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.JCodeGen;
import water.util.SBPrintStream;
import water.util.TwoDimTable;

public class PCAModel extends Model<PCAModel,PCAModel.PCAParameters,PCAModel.PCAOutput> {

  public static class PCAParameters extends Model.Parameters {
    public String algoName() { return "PCA"; }
    public String fullName() { return "Principal Components Analysis"; }
    public String javaName() { return PCAModel.class.getName(); }
    @Override public long progressUnits() { return _pca_method == PCAParameters.Method.GramSVD ? 5 : 3; }

    public DataInfo.TransformType _transform = DataInfo.TransformType.NONE; // Data transformation
    public Method _pca_method = Method.GramSVD;   // Method for computing PCA
    public int _k = 1;                     // Number of principal components
    public int _max_iterations = 1000;     // Max iterations
    public boolean _use_all_factor_levels = false;   // When expanding categoricals, should first level be kept or dropped?
    public boolean _compute_metrics = true;   // Should a second pass be made through data to compute metrics?
    public boolean _impute_missing = false;   // Should missing numeric values be imputed with the column mean?

    public enum Method {
      GramSVD, Power, Randomized, GLRM
    }
  }

  public static class PCAOutput extends Model.Output {
    // GLRM final value of loss function
    public double _objective;

    // Principal components (eigenvectors)
    public double[/*feature*/][/*k*/] _eigenvectors_raw;
    public TwoDimTable _eigenvectors;

    // Standard deviation of each principal component
    public double[] _std_deviation;

    // Importance of principal components
    // Standard deviation, proportion of variance explained, and cumulative proportion of variance explained
    public TwoDimTable _importance;

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

    public PCAOutput(PCA b) { super(b); }

    /** Override because base class implements ncols-1 for features with the
     *  last column as a response variable; for PCA all the columns are
     *  features. */
    @Override public int nfeatures() { return _names.length; }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.DimReduction;
    }
  }

  public PCAModel(Key selfKey, PCAParameters parms, PCAOutput output) { super(selfKey,parms,output); }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new ModelMetricsPCA.PCAModelMetrics(_parms._k);
  }

  @Override
  protected Frame predictScoreImpl(Frame orig, Frame adaptedFr, String destination_key, final Job j) {
    Frame adaptFrm = new Frame(adaptedFr);
    for(int i = 0; i < _parms._k; i++)
      adaptFrm.add("PC"+String.valueOf(i+1),adaptFrm.anyVec().makeZero());

    new MRTask() {
      @Override public void map( Chunk chks[] ) {
        if (isCancelled() || j != null && j.stop_requested()) return;
        double tmp [] = new double[_output._names.length];
        double preds[] = new double[_parms._k];
        for( int row = 0; row < chks[0]._len; row++) {
          double p[] = score0(chks, row, tmp, preds);
          for( int c=0; c<preds.length; c++ )
            chks[_output._names.length+c].set(row, p[c]);
        }
        if (j != null) j.update(1);
      }
    }.doAll(adaptFrm);

    // Return the projection into principal component space
    int x = _output._names.length, y = adaptFrm.numCols();
    Frame f = adaptFrm.extractFrame(x, y); // this will call vec_impl() and we cannot call the delete() below just yet

    f = new Frame(Key.<Frame>make(destination_key), f.names(), f.vecs());
    DKV.put(f);
    makeMetricBuilder(null).makeModelMetrics(this, orig, null, null);
    return f;
  }

  @Override
  protected double[] score0(double data[/*ncols*/], double preds[/*k*/]) {
    int numStart = _output._catOffsets[_output._catOffsets.length-1];
    assert data.length == _output._nnums + _output._ncats;

    for(int i = 0; i < _parms._k; i++) {
      preds[i] = 0;
      for (int j = 0; j < _output._ncats; j++) {
        double tmp = data[_output._permutation[j]];
        if (Double.isNaN(tmp)) continue;    // Missing categorical values are skipped
        int last_cat = _output._catOffsets[j+1]-_output._catOffsets[j]-1;
        int level = (int)tmp - (_parms._use_all_factor_levels ? 0:1);  // Reduce index by 1 if first factor level dropped during training
        if (level < 0 || level > last_cat) continue;  // Skip categorical level in test set but not in train
        preds[i] += _output._eigenvectors_raw[_output._catOffsets[j]+level][i];
      }

      int dcol = _output._ncats;
      int vcol = numStart;
      for (int j = 0; j < _output._nnums; j++) {
        preds[i] += (data[_output._permutation[dcol]] - _output._normSub[j]) * _output._normMul[j] * _output._eigenvectors_raw[vcol][i];
        dcol++; vcol++;
      }
    }
    return preds;
  }

  @Override protected SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
    sb = super.toJavaInit(sb, fileCtx);
    sb.ip("public boolean isSupervised() { return " + isSupervised() + "; }").nl();
    sb.ip("public int nfeatures() { return "+_output.nfeatures()+"; }").nl();
    sb.ip("public int nclasses() { return "+_parms._k+"; }").nl();

    // This is model name
    final String mname = JCodeGen.toJavaId(_key.toString());

    fileCtx.add(new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        if (_output._nnums > 0) {
          JCodeGen.toClassWithArray(out, null, mname + "_NORMMUL", _output._normMul,
                                    "Standardization/Normalization scaling factor for numerical variables.");
          JCodeGen.toClassWithArray(out, null, mname + "_NORMSUB", _output._normSub,
                                    "Standardization/Normalization offset for numerical variables.");
        }
        JCodeGen.toClassWithArray(out, null, mname + "_CATOFFS", _output._catOffsets,
                                  "Categorical column offsets.");
        JCodeGen.toClassWithArray(out, null, mname + "_PERMUTE", _output._permutation,
                                  "Permutation index vector.");
        JCodeGen.toClassWithArray(out, null, mname + "_EIGVECS", _output._eigenvectors_raw,
                                  "Eigenvector matrix.");
      }
    });


    return sb;
  }

  @Override protected void toJavaPredictBody(SBPrintStream bodySb,
                                             CodeGeneratorPipeline classCtx,
                                             CodeGeneratorPipeline fileCtx,
                                             final boolean verboseCode) {
    // This is model name
    final String mname = JCodeGen.toJavaId(_key.toString());

    bodySb.i().p("java.util.Arrays.fill(preds,0);").nl();
    final int cats = _output._ncats;
    final int nums = _output._nnums;
    bodySb.i().p("final int nstart = ").pj(mname+"_CATOFFS", "VALUES").p("[").pj(mname+"_CATOFFS", "VALUES").p(".length-1];").nl();
    bodySb.i().p("for(int i = 0; i < ").p(_parms._k).p("; i++) {").nl();

    // Categorical columns
    bodySb.i(1).p("for(int j = 0; j < ").p(cats).p("; j++) {").nl();
    bodySb.i(2).p("double d = data[").pj(mname+"_PERMUTE", "VALUES").p("[j]];").nl();
    bodySb.i(2).p("if(Double.isNaN(d)) continue;").nl();
    bodySb.i(2).p("int last = ").pj(mname+"_CATOFFS", "VALUES").p("[j+1]-").pj(mname+"_CATOFFS", "VALUES").p("[j]-1;").nl();
    bodySb.i(2).p("int c = (int)d").p(_parms._use_all_factor_levels ? ";":"-1;").nl();
    bodySb.i(2).p("if(c < 0 || c > last) continue;").nl();
    bodySb.i(2).p("preds[i] += ").pj(mname+"_EIGVECS", "VALUES").p("[").pj(mname+"_CATOFFS", "VALUES").p("[j]+c][i];").nl();
    bodySb.i(1).p("}").nl();

    // Numeric columns
    bodySb.i(1).p("for(int j = 0; j < ").p(nums).p("; j++) {").nl();
    bodySb.i(2).p("preds[i] += (data[").pj(mname+"_PERMUTE", "VALUES").p("[j" + (cats > 0 ? "+" + cats : "") + "]]-").pj(mname+"_NORMSUB", "VALUES").p("[j])*").pj(mname+"_NORMMUL", "VALUES").p("[j]*").pj(mname+"_EIGVECS", "VALUES").p("[j" + (cats > 0 ? "+ nstart" : "") +"][i];").nl();
    bodySb.i(1).p("}").nl();
    bodySb.i().p("}").nl();
  }
}
