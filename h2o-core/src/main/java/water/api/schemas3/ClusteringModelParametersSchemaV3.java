package water.api.schemas3;

import hex.ClusteringModel;
import water.api.API;

/**
 * An instance of a ClusteringModelParameters schema contains the common ClusteringModel build parameters.
 */
public class ClusteringModelParametersSchemaV3<P extends ClusteringModel.ClusteringParameters, S extends ClusteringModelParametersSchemaV3<P, S>> extends ModelParametersSchemaV3<P, S> {
  static public String[] own_fields = new String[] { "k" };

  @API(help = "The max. number of clusters. If estimate_k is disabled, the model will find k centroids, otherwise it will find up to k centroids.", required = true, direction = API.Direction.INOUT, gridable = true)
  public int k;
}
