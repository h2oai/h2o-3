package hex.schemas;

import hex.naivebayes.NaiveBayes;
import hex.naivebayes.NaiveBayesModel;
import water.api.API;
import water.api.SupervisedModelParametersSchema;

public class NaiveBayesV2 extends SupervisedModelBuilderSchema<NaiveBayes,NaiveBayesV2,NaiveBayesV2.NaiveBayesParametersV2> {
  public static final class NaiveBayesParametersV2 extends SupervisedModelParametersSchema<NaiveBayesModel.NaiveBayesParameters, NaiveBayesParametersV2> {
    static public String[] own_fields = new String[]{"laplace", "min_sdev", "eps_sdev", "min_prob", "eps_prob"};

    @API(help = "Laplace smoothing parameter")
    public double laplace;

    @API(help = "Min. standard deviation to use for observations with not enough data")
    public double min_sdev;

    @API(help = "Cutoff below which standard deviation is replaced with min_sdev")
    public double eps_sdev;

    @API(help = "Min. probability to use for observations with not enough data")
    public double min_prob;

    @API(help = "Cutoff below which standard deviation is replaced with min_sdev")
    public double eps_prob;
  }
}
