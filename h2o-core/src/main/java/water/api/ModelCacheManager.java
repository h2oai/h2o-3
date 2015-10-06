package water.api;

import hex.Model;
import water.api.ModelsHandler.Models;

public class ModelCacheManager {
  public static <M extends Model<M,P,?>, P extends Model.Parameters> M get(P parms) {
    Model[] models = Models.fetchAll();
    for(int i = 0; i < models.length; i++) {
      if(null != models[i]._parms && models[i]._parms.checksum() == parms.checksum())
        return (M)models[i];
    }
    return null;
  }
}