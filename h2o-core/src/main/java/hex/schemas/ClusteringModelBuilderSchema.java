package hex.schemas;

import hex.ClusteringModelBuilder;
import water.api.schemas3.ClusteringModelParametersSchemaV3;

public class ClusteringModelBuilderSchema<B extends ClusteringModelBuilder, S extends ClusteringModelBuilderSchema<B,S,P>, P
    extends ClusteringModelParametersSchemaV3> extends ModelBuilderSchema<B,S,P> {
}