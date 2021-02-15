package hex.schemas;

import hex.tree.uplift.UpliftDRF;
import hex.tree.uplift.UpliftDRFModel.UpliftDRFParameters;
import water.api.API;

public class UpliftDRFV3 extends SharedTreeV3<UpliftDRF, UpliftDRFV3, UpliftDRFV3.UpliftDRFParametersV3> {

    public static final class UpliftDRFParametersV3 extends SharedTreeV3.SharedTreeParametersV3<UpliftDRFParameters, UpliftDRFParametersV3> {
        static public String[] fields = new String[]{
                "model_id",
                "training_frame",
                "validation_frame",
                "nfolds",
                "keep_cross_validation_models",
                "keep_cross_validation_predictions",
                "keep_cross_validation_fold_assignment",
                "score_each_iteration",
                "score_tree_interval",
                "fold_assignment",
                "fold_column",
                "response_column",
                "ignored_columns",
                "ignore_const_cols",
                "offset_column",
                "weights_column",
                "balance_classes",
                "class_sampling_factors",
                "max_after_balance_size",
                "max_confusion_matrix_size",
                "ntrees",
                "max_depth",
                "min_rows",
                "nbins",
                "nbins_top_level",
                "nbins_cats",
                "stopping_rounds",
                "stopping_metric",
                "stopping_tolerance",
                "max_runtime_secs",
                "seed",
                "mtries",
                "sample_rate",
                "sample_rate_per_class",
                "binomial_double_trees",
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
                "uplift_column",
                "uplift_metric"
        };

        // Input fields
        @API(help = "Number of variables randomly sampled as candidates at each split. If set to -1, defaults to sqrt{p} for classification and p/3 for regression (where p is the # of predictors", gridable = true)
        public int mtries;

        @API(help = "For binary classification: Build 2x as many trees (one per class) - can lead to higher accuracy.", level = API.Level.expert)
        public boolean binomial_double_trees;

        @API(help = "Row sample rate per tree (from 0.0 to 1.0)", gridable = true)
        public double sample_rate;

    }
}
