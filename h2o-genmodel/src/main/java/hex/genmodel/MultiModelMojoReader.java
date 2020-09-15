package hex.genmodel;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class MultiModelMojoReader<M extends MojoModel> extends ModelMojoReader<M> {

  private Map<String, MojoModel> _subModels;

  @Override
  protected final void readModelData() throws IOException {
    int subModelCount = readkv("submodel_count", 0);
    HashMap<String, MojoModel> models = new HashMap<>(subModelCount);
    for (int i = 0; i < subModelCount; i++) {
      String key = readkv("submodel_key_" + i);
      String zipDirectory = readkv("submodel_dir_" + i);
      MojoModel model = ModelMojoReader.readFrom(new NestedMojoReaderBackend(_reader, zipDirectory));
      models.put(key, model);
    }
    _subModels = Collections.unmodifiableMap(models);
    readParentModelData();
  }

  protected MojoModel getModel(String key) {
    return _subModels.get(key);
  }

  protected Map<String, MojoModel> getSubModels() {
    return _subModels;
  }

  protected abstract void readParentModelData() throws IOException;

}
