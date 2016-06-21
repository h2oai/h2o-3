package water.api.schemas3;

import hex.ClusteringModel;
import water.api.API;

/**
 * An instance of a ClusteringModelParameters schema contains the common ClusteringModel build parameters.
 */
public class ClusteringModelParametersSchemaV3<P extends ClusteringModel.ClusteringParameters, S extends ClusteringModelParametersSchemaV3<P, S>> extends ModelParametersSchemaV3<P, S> {
  static public String[] own_fields = new String[] { "k" };

  @API(help = "Number of clusters", required = true, direction = API.Direction.INOUT, gridable = true)
  public int k;
}
