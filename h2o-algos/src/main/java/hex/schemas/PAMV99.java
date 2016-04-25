package hex.schemas;

import hex.pam.PAM;
import hex.pam.PAMModel;
import water.api.API;
import water.api.ModelParametersSchema;

public class PAMV99 extends ModelBuilderSchema<PAM,PAMV99,PAMV99.PAMParametersV99> {

  public static final class PAMParametersV99 extends ModelParametersSchema<PAMModel.PAMParameters, PAMParametersV99> {
    static public String[] fields = new String[] {
            "training_frame",
            "ignored_columns",
            "dissimilarity_measure",
            "do_swap",
            "initial_medoids"};

    @API(help="dissimilarity_measure", values = {"MANHATTAN", "EUCLIDEAN" }) public PAM.DissimilarityMeasure dissimilarity_measure;
    @API(help="do_swap") public boolean do_swap;
    @API(help="initial_medoids") public long[] initial_medoids;
  }
}
