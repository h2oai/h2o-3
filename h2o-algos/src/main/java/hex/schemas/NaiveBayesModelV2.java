package hex.schemas;

import hex.naivebayes.NaiveBayesModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;

public class NaiveBayesModelV2 extends ModelSchema<NaiveBayesModel, NaiveBayesModelV2, NaiveBayesModel.NaiveBayesParameters, NaiveBayesV2.NaiveBayesParametersV2, NaiveBayesModel.NaiveBayesOutput, NaiveBayesModelV2.NaiveBayesModelOutputV2> {
  public static final class NaiveBayesModelOutputV2 extends ModelOutputSchema<NaiveBayesModel.NaiveBayesOutput, NaiveBayesModelOutputV2> {
    // Output fields; input fields are in the parameters list
    @API(help = "Model parameters")
    NaiveBayesV2.NaiveBayesParametersV2 parameters;

    @API(help = "Class counts of the dependent variable")
    public double[] rescnt;

    @API(help = "Class distribution of the dependent variable")
    public double[] pprior;

    @API(help = "For every predictor variable, a table giving, for each attribute level, the conditional probabilities given the target class")
    public double[][][] pcond;
  }

  // TODO: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public NaiveBayesV2.NaiveBayesParametersV2 createParametersSchema() { return new NaiveBayesV2.NaiveBayesParametersV2(); }
  public NaiveBayesModelOutputV2 createOutputSchema() { return new NaiveBayesModelOutputV2(); }

  // Version&Schema-specific filling into the impl
  @Override public NaiveBayesModel createImpl() {
    NaiveBayesModel.NaiveBayesParameters parms = parameters.createImpl();
    return new NaiveBayesModel( key.key(), parms, null );
  }
}
