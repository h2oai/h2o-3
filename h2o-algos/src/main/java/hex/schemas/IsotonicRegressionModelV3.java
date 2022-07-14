package hex.schemas;

import hex.isotonic.IsotonicRegressionModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class IsotonicRegressionModelV3 extends ModelSchemaV3<
        IsotonicRegressionModel, IsotonicRegressionModelV3,
        IsotonicRegressionModel.IsotonicRegressionParameters, IsotonicRegressionV3.IsotonicRegressionParametersV3,
        IsotonicRegressionModel.IsotonicRegressionOutput, IsotonicRegressionModelV3.IsotonicRegressionModelOutputV3
    > {

    public static final class IsotonicRegressionModelOutputV3 extends ModelOutputSchemaV3<IsotonicRegressionModel.IsotonicRegressionOutput, IsotonicRegressionModelOutputV3> {
        @API(help = "thresholds y")
        public double[] thresholds_y;
        @API(help = "thresholds X")
        public double[] thresholds_x;
        @API(help = "min X")
        public double min_x;
        @API(help = "max X")
        public double max_x;

        @Override
        public IsotonicRegressionModelOutputV3 fillFromImpl(IsotonicRegressionModel.IsotonicRegressionOutput impl) {
            super.fillFromImpl(impl);
            return this;
        }
    } // IsotonicRegressionOutputV3

    public IsotonicRegressionV3.IsotonicRegressionParametersV3 createParametersSchema() { return new IsotonicRegressionV3.IsotonicRegressionParametersV3(); }
    public IsotonicRegressionModelOutputV3 createOutputSchema() { return new IsotonicRegressionModelOutputV3(); }

}
