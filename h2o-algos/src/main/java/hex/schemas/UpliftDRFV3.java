package hex.schemas;

import hex.AUUC;
import hex.tree.uplift.UpliftDRF;
import hex.tree.uplift.UpliftDRFModel.UpliftDRFParameters;
import water.api.API;

public class UpliftDRFV3 extends SharedTreeV3<UpliftDRF, UpliftDRFV3, UpliftDRFV3.UpliftDRFParametersV3> {

    public static final class UpliftDRFParametersV3 extends SharedTreeV3.SharedTreeParametersV3<UpliftDRFParameters, UpliftDRFParametersV3> {
        static public String[] fields = new String[]{
                "model_id",
                "training_frame",
                "validation_frame",
                "score_each_iteration",
                "score_tree_interval",
                "response_column",
                "ignored_columns",
                "ignore_const_cols",
                "balance_classes",
                "class_sampling_factors",
                "max_after_balance_size",
                "ntrees",
                "max_depth",
                "min_rows",
                "nbins",
                "nbins_top_level",
                "nbins_cats",
                "max_runtime_secs",
                "seed",
                "mtries",
                "sample_rate",
                "sample_rate_per_class",
                "checkpoint",
                "col_sample_rate_change_per_level",
                "col_sample_rate_per_tree",
                "histogram_type",
                "categorical_encoding",
                "calibrate_model",
                "calibration_frame",
                "distribution",
                "custom_metric_func",
                "export_checkpoints_dir",
                "check_constant_response",
                "gainslift_bins",
                "treatment_column",
                "uplift_metric",
                "auuc_type"
        };

        // Input fields
        @API(help = "Number of variables randomly sampled as candidates at each split. If set to -1, defaults to sqrt{p} for classification and p/3 for regression (where p is the # of predictors", gridable = true)
        public int mtries;

        @API(help = "Row sample rate per tree (from 0.0 to 1.0)", gridable = true)
        public double sample_rate;

        @API(help = "Define column which will be use for computing uplift gain to select best split for a tree. The column has to devide dataset into treatment (value 1) and control (value 0) group.", gridable = false, level = API.Level.secondary, required = true,
                is_member_of_frames = {"training_frame", "validation_frame"},
                is_mutually_exclusive_with = {"ignored_columns","response_column", "weights_column"})
        public String treatment_column;

        @API(help = "Divergence metric used to find best split when building an upplift tree.", level = API.Level.secondary, values = { "AUTO", "KL", "Euclidean", "ChiSquared"})
        public UpliftDRFParameters.UpliftMetricType uplift_metric;

        @API(help = "AUUC metric used to calculate Area under Uplift.", level = API.Level.secondary, values = { "AUTO", "Qini", "Lift", "Gain"})
        public AUUC.AUUCType auuc_type;


    }
}
