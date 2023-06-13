package hex.schemas;

import hex.isotonic.IsotonicRegression;
import hex.isotonic.IsotonicRegressionModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class IsotonicRegressionV3 extends ModelBuilderSchema<IsotonicRegression, IsotonicRegressionV3, IsotonicRegressionV3.IsotonicRegressionParametersV3> {

    public static final class IsotonicRegressionParametersV3 
            extends ModelParametersSchemaV3<IsotonicRegressionModel.IsotonicRegressionParameters, IsotonicRegressionV3.IsotonicRegressionParametersV3> {
        public static String[] fields = new String[]{
                "model_id",
                "training_frame",
                "validation_frame",
                "response_column",
                "ignored_columns",
                "weights_column",
                "out_of_bounds",
                "custom_metric_func",
                "nfolds",
                "keep_cross_validation_models",
                "keep_cross_validation_predictions",
                "keep_cross_validation_fold_assignment",
                "fold_assignment",
                "fold_column"
        };

        @API(help="Method of handling values of X predictor that are outside of the bounds seen in training.", values = {"NA", "clip"}, direction = API.Direction.INOUT)
        public IsotonicRegressionModel.OutOfBoundsHandling out_of_bounds;

    }

}
