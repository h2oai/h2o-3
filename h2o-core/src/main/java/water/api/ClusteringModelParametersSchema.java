package water.api;

import hex.ClusteringModel;

/**
 * An instance of a ClusteringModelParameters schema contains the common ClusteringModel build parameters.
 */
abstract public class ClusteringModelParametersSchema<P extends ClusteringModel.ClusteringParameters, S extends ClusteringModelParametersSchema<P, S>> extends ModelParametersSchema<P, S> {
  @API(help = "Number of clusters", required = true)
  public int k;
}
