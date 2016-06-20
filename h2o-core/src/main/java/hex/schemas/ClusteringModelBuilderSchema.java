package hex.schemas;

import hex.ClusteringModelBuilder;
import water.api.ClusteringModelParametersSchema;

public class ClusteringModelBuilderSchema<B extends ClusteringModelBuilder, S extends ClusteringModelBuilderSchema<B,S,P>, P
    extends ClusteringModelParametersSchema> extends ModelBuilderSchema<B,S,P> {
}