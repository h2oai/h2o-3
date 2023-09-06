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

                // Extended Isolation Forest specific
                "n_estimators",
                "weak_learner",
                "learning_rate",
                "seed",
        };

        @API(help = "Number of AdaBoost weak learners.", gridable = true)
        public int n_estimators;

        @API(help = "Weak learner", gridable = true, values = {"AUTO", "DRF", "GLM"})
        public AdaBoostModel.Algorithm weak_learner;

        @API(help = "Learning rate", gridable = true)
        public double learning_rate;

        @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
        public long seed;
    }
}
