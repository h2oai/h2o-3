package hex.schemas;

import hex.naivebayes.NaiveBayes;
import hex.naivebayes.NaiveBayesModel;
import water.api.API;
import water.api.ModelParametersSchema;

public class NaiveBayesV2 extends ModelBuilderSchema<NaiveBayes,NaiveBayesV2,NaiveBayesV2.NaiveBayesParametersV2> {
  public static final class NaiveBayesParametersV2 extends ModelParametersSchema<NaiveBayesModel.NaiveBayesParameters, NaiveBayesParametersV2> {
    @API(help = "Laplace smoothing parameter")
    public double laplace;

    @API(help = "Min. standard deviation to use for observations with not enough data")
    public double min_sdev;
  }
}
