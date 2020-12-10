package water.api.schemas3;

import hex.Model.Parameters.CategoricalEncodingScheme;
import hex.Model.Parameters.FoldAssignmentScheme;
import hex.MultinomialAucType;
import hex.ScoreKeeper.StoppingMetric;
import hex.genmodel.utils.DistributionFamily;
import water.api.EnumValuesProvider;

public interface ModelParamsValuesProviders {

  class StoppingMetricValuesProvider extends EnumValuesProvider<StoppingMetric> {
    public StoppingMetricValuesProvider() {
      super(StoppingMetric.class);
    }
  }

  class DistributionFamilyValuesProvider extends EnumValuesProvider<DistributionFamily> {
    public DistributionFamilyValuesProvider() {
      super(DistributionFamily.class, new DistributionFamily[]{DistributionFamily.modified_huber});
    }
  }

  class CategoricalEncodingSchemeValuesProvider extends EnumValuesProvider<CategoricalEncodingScheme> {
    public CategoricalEncodingSchemeValuesProvider() {
      super(CategoricalEncodingScheme.class);
    }
  }

  class FoldAssignmentSchemeValuesProvider extends EnumValuesProvider<FoldAssignmentScheme> {
    public FoldAssignmentSchemeValuesProvider() {
      super(FoldAssignmentScheme.class);
    }
  }
  
  class MultinomialAucTypeSchemeValuesProvider extends EnumValuesProvider<MultinomialAucType>{
    public MultinomialAucTypeSchemeValuesProvider() {super(MultinomialAucType.class);}
  }
}
