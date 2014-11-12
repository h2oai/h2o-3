package hex.schemas;

import hex.SupervisedModelBuilder;
import water.api.SupervisedModelParametersSchema;

public abstract class SupervisedModelBuilderSchema<B extends SupervisedModelBuilder, S extends SupervisedModelBuilderSchema<B,S,P>, P extends SupervisedModelParametersSchema> extends ModelBuilderSchema<B,S,P> {
}
