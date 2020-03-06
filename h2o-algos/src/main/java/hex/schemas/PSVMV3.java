package hex.schemas;

import hex.genmodel.algos.psvm.KernelType;
import hex.psvm.PSVM;
import hex.psvm.PSVMModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class PSVMV3 extends ModelBuilderSchema<PSVM, PSVMV3, PSVMV3.PSVMParametersV3> {

  public static final class PSVMParametersV3 extends ModelParametersSchemaV3<PSVMModel.PSVMParameters, PSVMParametersV3> {
    public static final String[] fields = new String[]{
            "model_id",
            "training_frame",
            "validation_frame",
            "response_column",
            "ignored_columns",
            "ignore_const_cols",
            "hyper_param",
            "kernel_type",
            "gamma",
            "rank_ratio",
            "positive_weight",
            "negative_weight",
            "disable_training_metrics",
            "sv_threshold",
            "fact_threshold",
            "feasible_threshold",
            "surrogate_gap_threshold",
            "mu_factor",
            "max_iterations",
            "seed",
            "te_model_id"
    };

    @API(help = "Penalty parameter C of the error term", gridable = true)
    public double hyper_param;

    @API(help = "Type of used kernel", values = {"gaussian"})
    public KernelType kernel_type;

    @API(help = "Coefficient of the kernel (currently RBF gamma for gaussian kernel, -1 means 1/#features)", gridable = true)
    public double gamma;

    @API(help = "Desired rank of the ICF matrix expressed as an ration of number of input rows (-1 means use sqrt(#rows)).", gridable = true)
    public double rank_ratio;

    @API(help = "Weight of positive (+1) class of observations")
    public double positive_weight;

    @API(help = "Weight of positive (-1) class of observations")
    public double negative_weight;

    @API(help = "Disable calculating training metrics (expensive on large datasets)")
    public boolean disable_training_metrics;
    
    @API(help = "Threshold for accepting a candidate observation into the set of support vectors", level = API.Level.secondary)
    public double sv_threshold;

    @API(help = "Maximum number of iteration of the algorithm", level = API.Level.secondary)
    public int max_iterations;
    
    @API(help = "Convergence threshold of the Incomplete Cholesky Factorization (ICF)", level = API.Level.expert)
    public double fact_threshold;

    @API(help = "Convergence threshold for primal-dual residuals in the IPM iteration", level = API.Level.expert)
    public double feasible_threshold;

    @API(help = "Feasibility criterion of the surrogate duality gap (eta)", level = API.Level.expert)
    public double surrogate_gap_threshold;

    @API(help = "Increasing factor mu", level = API.Level.expert)
    public double mu_factor;

    @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
    public long seed;

  }

}
