package hex.schemas;

import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import water.api.API;

public class IsolationForestV3 extends SharedTreeV3<IsolationForest, IsolationForestV3, IsolationForestV3.IsolationForestParametersV3> {

    public static final class IsolationForestParametersV3 extends SharedTreeV3.SharedTreeParametersV3<IsolationForestModel.IsolationForestParameters, IsolationForestParametersV3> {
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
                "max_hit_ratio_k",
                "ntrees",
                "max_depth",
                "min_rows",
                "nbins",
                "nbins_top_level",
                "nbins_cats",
                "r2_stopping",
                "stopping_rounds",
                "stopping_metric",
                "stopping_tolerance",
                "max_runtime_secs",
                "seed",
                "build_tree_one_node",
                "mtries",
                "sample_rate",
                "sample_rate_per_class",
                "checkpoint",
                "col_sample_rate_change_per_level",
                "col_sample_rate_per_tree",
                "min_split_improvement",
                "histogram_type",
                "categorical_encoding",
                "calibrate_model",
                "calibration_frame",
                "distribution",
                "custom_metric_func"
        };

        // Input fields
        @API(help = "Number of variables randomly sampled as candidates at each split. If set to -1, defaults p/3 for regression (where p is the # of predictors)", gridable = true)
        public int mtries;
    }
}
