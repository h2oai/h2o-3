package hex.schemas;

import hex.StackedEnsembleModel;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

/**
 * Created by rpeck on 10/11/16.
 */
public class StackedEnsembleModelV99 extends ModelSchemaV3<StackedEnsembleModel, StackedEnsembleModelV99, StackedEnsembleModel.StackedEnsembleParameters, StackedEnsembleV99.StackedEnsembleParametersV99, StackedEnsembleModel.StackedEnsembleOutput, StackedEnsembleModelV99.StackedEnsembleModelOutputV99> {

  public static final class StackedEnsembleModelOutputV99 extends ModelOutputSchemaV3<StackedEnsembleModel.StackedEnsembleOutput, StackedEnsembleModelOutputV99> {
  }

  public StackedEnsembleV99.StackedEnsembleParametersV99 createParametersSchema() { return new StackedEnsembleV99.StackedEnsembleParametersV99(); }
  public StackedEnsembleModelOutputV99 createOutputSchema() { return new StackedEnsembleModelOutputV99(); }

}

