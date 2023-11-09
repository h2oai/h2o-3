package hex.schemas;

import hex.adaboost.AdaBoost;
import hex.adaboost.AdaBoostModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class AdaBoostV3 extends ModelBuilderSchema<
        AdaBoost,
        AdaBoostV3, 
        AdaBoostV3.AdaBoostParametersV3> {

    public static final class AdaBoostParametersV3 extends ModelParametersSchemaV3<AdaBoostModel.AdaBoostParameters, AdaBoostParametersV3> {
        static public String[] fields = new String[]{
                "model_id",
                "training_frame",
                "ignored_columns",
                "ignore_const_cols",
                "categorical_encoding",
                "weights_column",

                // AdaBoost specific
                "nlearners",
                "weak_learner",
                "learn_rate",
                "weak_learner_params",
                "seed",
        };

        @API(help = "Number of AdaBoost weak learners.", gridable = true)
        public int nlearners;

        @API(help = "Choose a weak learner type. Defaults to AUTO, which means DRF.", gridable = true, values = {"AUTO", "DRF", "GLM", "GBM", "DEEP_LEARNING"})
        public AdaBoostModel.Algorithm weak_learner;

        @API(help="Learning rate (from 0.0 to 1.0)", gridable = true)
        public double learn_rate;

        @API(help = "Customized parameters for the weak_learner algorithm.", gridable=true)
        public String weak_learner_params;

        @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
        public long seed;
    }
}
