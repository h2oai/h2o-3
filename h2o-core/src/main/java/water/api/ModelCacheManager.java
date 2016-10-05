package water.api;

import hex.Model;
import water.api.ModelsHandler.Models;

public class ModelCacheManager {

  public static <M extends Model<M,P,? extends Model.Output>, P extends Model.Parameters> M get(P parms) {
    Model[] models = Models.fetchAll();
    long checksum = parms.checksum();
    for (Model model : models) {
      if (model._parms != null && model._parms.checksum() == checksum)
        return (M) model;
    }
    return null;
  }

}
