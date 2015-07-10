package water.api;

import hex.ClusteringModel;

/**
 * An instance of a ClusteringModelParameters schema contains the common ClusteringModel build parameters.
 */
public class ClusteringModelParametersSchema<P extends ClusteringModel.ClusteringParameters, S extends ClusteringModelParametersSchema<P, S>> extends ModelParametersSchema<P, S> {
  static public String[] fields = new String[] { "k" };

  @API(help = "Number of clusters", required = true, direction = API.Direction.INOUT, gridable = true)
  public int k;
}
