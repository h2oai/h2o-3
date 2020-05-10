package hex.schemas;

import hex.genmodel.utils.DistributionFamily;
import hex.tree.SharedTreeModel;
import hex.tree.isoforextended.ExtendedIsolationForest;
import hex.tree.isoforextended.ExtendedIsolationForestModel;
import water.api.API;

public class ExtendedIsolationForestV3 extends SharedTreeV3<
        ExtendedIsolationForest,
        ExtendedIsolationForestV3, 
        ExtendedIsolationForestV3.ExtendedIsolationForestParametersV3> {

    public static final class ExtendedIsolationForestParametersV3 extends SharedTreeV3.SharedTreeParametersV3<ExtendedIsolationForestModel.ExtendedIsolationForestParameters, ExtendedIsolationForestParametersV3> {
        static public String[] fields = new String[]{
                "model_id",
                "training_frame",
                "score_each_iteration",
                "score_tree_interval",
                "ignored_columns",
                "ignore_const_cols",
                "ntrees",
                "max_depth",
                "min_rows",
                "max_runtime_secs",
                "seed",
                "build_tree_one_node",
                "sample_size",
                "extension_level",
                "col_sample_rate_change_per_level",
                "col_sample_rate_per_tree",
                "categorical_encoding",
                "stopping_rounds",
                "stopping_metric",
                "stopping_tolerance",
                "export_checkpoints_dir"
        };

        // Input fields
        @API(help = "Number of randomly sampled observations used to train each Extended Isolation Forest tree.", gridable = true)
        public long sample_size;
        
        @API(help = "Maximum is N - 1 (N = numCols). Minimum is 0. Extended Isolation Forest " +
                "with extension_Level = 0 behaves like Isolation Forest.", gridable = true)
        public int extension_level;
    }
}
