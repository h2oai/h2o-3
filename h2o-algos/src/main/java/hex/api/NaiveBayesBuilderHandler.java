package hex.api;

import hex.naivebayes.NaiveBayes;
import hex.schemas.NaiveBayesV3;
import water.api.ModelBuilderHandler;

public class NaiveBayesBuilderHandler extends ModelBuilderHandler<NaiveBayes, NaiveBayesV3, NaiveBayesV3.NaiveBayesParametersV3> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NaiveBayesV3 train(int version, NaiveBayesV3 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NaiveBayesV3 validate_parameters(int version, NaiveBayesV3 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}
