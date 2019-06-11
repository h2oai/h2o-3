package hex.schemas;

import hex.svm.SVM;
import hex.svm.SVMModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class SVMV3 extends ModelBuilderSchema<SVM, SVMV3, SVMV3.SVMParametersV3> {

  public static final class SVMParametersV3 extends ModelParametersSchemaV3<SVMModel.SVMParameters, SVMV3.SVMParametersV3> {
    public static final String[] fields = new String[]{
            "model_id",
            "training_frame",
            "validation_frame",
            "seed",
            "response_column",
            "ignored_columns",
            "ignore_const_cols",
    };

    @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
    public long seed;

  }

}
