package hex.schemas;

import hex.naivebayes.NaiveBayes;
import hex.naivebayes.NaiveBayesModel;
import water.api.API;
import water.api.SupervisedModelParametersSchema;

public class NaiveBayesV3 extends SupervisedModelBuilderSchema<NaiveBayes,NaiveBayesV3,NaiveBayesV3.NaiveBayesParametersV3> {
  public static final class NaiveBayesParametersV3 extends SupervisedModelParametersSchema<NaiveBayesModel.NaiveBayesParameters, NaiveBayesParametersV3> {
    static public String[] own_fields = new String[]{"laplace", "min_sdev", "eps_sdev", "min_prob", "eps_prob"};

    @API(help = "Laplace smoothing parameter")
    public double laplace;

    @API(help = "Min. standard deviation to use for observations with not enough data")
    public double min_sdev;

    @API(help = "Cutoff below which standard deviation is replaced with min_sdev")
    public double eps_sdev;

    @API(help = "Min. probability to use for observations with not enough data")
    public double min_prob;

    @API(help = "Cutoff below which probability is replaced with min_prob")
    public double eps_prob;
  }
}
