package hex.api;

import hex.naivebayes.NaiveBayes;
import hex.schemas.NaiveBayesV2;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class NaiveBayesBuilderHandler extends ModelBuilderHandler<NaiveBayes, NaiveBayesV2, NaiveBayesV2.NaiveBayesParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, NaiveBayesV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NaiveBayesV2 validate_parameters(int version, NaiveBayesV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}
