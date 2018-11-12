package hex.schemas;

import hex.DataInfo;
import hex.pca.PCA;
import hex.pca.PCAModel.PCAParameters;
import hex.pca.PCAImplementation;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class PCAV3 extends ModelBuilderSchema<PCA,PCAV3,PCAV3.PCAParametersV3> {

  public static final class PCAParametersV3 extends ModelParametersSchemaV3<PCAParameters, PCAParametersV3> {
    static public String[] fields = new String[]{
        "model_id",
        "training_frame",
        "validation_frame",
        "ignored_columns",
        "ignore_const_cols",
        "score_each_iteration",
        "transform",
        "pca_method",
        "pca_impl",
        "k",
        "max_iterations",
        "use_all_factor_levels",
        "compute_metrics",
        "impute_missing",
        "seed",
        "max_runtime_secs",
        "export_checkpoints_dir"
    };

    @API(help = "Transformation of training data", values = { "NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE" }, gridable = true)  // TODO: pull out of categorical class
    public DataInfo.TransformType transform;

    @API(
            help =  "Specify the algorithm to use for computing the principal components: " +
                    "GramSVD - uses a distributed computation of the Gram matrix, followed by a local SVD; " +
                    "Power - computes the SVD using the power iteration method (experimental); " +
                    "Randomized - uses randomized subspace iteration method; " +
                    "GLRM - fits a generalized low-rank model with L2 loss function and no regularization and solves for the SVD using local matrix algebra (experimental)",
            values = { "GramSVD", "Power", "Randomized", "GLRM" })   // TODO: pull out of categorical class
    public PCAParameters.Method pca_method;
  
    @API(
            help =  "Specify the implementation to use for computing PCA (via SVD or EVD): " +
                    "MTJ_EVD_DENSEMATRIX - eigenvalue decompositions for dense matrix using MTJ; " +
                    "MTJ_EVD_SYMMMATRIX - eigenvalue decompositions for symmetric matrix using MTJ; " +
                    "MTJ_SVD_DENSEMATRIX - singular-value decompositions for dense matrix using MTJ; " +
                    "JAMA - eigenvalue decompositions for dense matrix using JAMA. " +
                    "References: " +
                    "JAMA - http://math.nist.gov/javanumerics/jama/; " +
                    "MTJ - https://github.com/fommil/matrix-toolkits-java/",
            values = { "MTJ_EVD_DENSEMATRIX", "MTJ_EVD_SYMMMATRIX", "MTJ_SVD_DENSEMATRIX", "JAMA" })
    public PCAImplementation pca_impl;
    
    @API(help = "Rank of matrix approximation", required = true, direction = API.Direction.INOUT, gridable = true)
    public int k;

    @API(help = "Maximum training iterations", direction = API.Direction.INOUT, gridable = true)
    public int max_iterations;

    @API(help = "RNG seed for initialization", direction = API.Direction.INOUT)
    public long seed;

    @API(help = "Whether first factor level is included in each categorical expansion", direction = API.Direction.INOUT)
    public boolean use_all_factor_levels;

    @API(help = "Whether to compute metrics on the training data", direction = API.Direction.INOUT)
    public boolean compute_metrics;

    @API(help = "Whether to impute missing entries with the column mean", direction = API.Direction.INOUT)
    public boolean impute_missing;
  }
}
