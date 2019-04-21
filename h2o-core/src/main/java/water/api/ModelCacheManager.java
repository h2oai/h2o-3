package water.api;

import hex.Model;

public class ModelCacheManager {

  public static <M extends Model, P extends Model.Parameters> M get(P parms) {
    Model[] models = Model.fetchAll();
    long checksum = parms.checksum();
    for (Model model : models) {
      if (model._parms != null && model._parms.checksum() == checksum)
        return (M) model;
    }
    return null;
  }

}
