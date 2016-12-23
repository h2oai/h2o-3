package water.api;

import hex.Model;
import water.http.handlers.ModelsHandler;

public class ModelCacheManager {

  public static <M extends Model, P extends Model.Parameters> M get(P parms) {
    Model[] models = ModelsHandler.Models.fetchAll();
    long checksum = parms.checksum();
    for (Model model : models) {
      if (model._parms != null && model._parms.checksum() == checksum)
        return (M) model;
    }
    return null;
  }

}
