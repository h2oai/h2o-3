package hex;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipOutputStream;

public abstract class MultiModelMojoWriter<M extends Model<M, P, O>, P extends Model.Parameters, O extends Model.Output>
  extends ModelMojoWriter<M, P, O> {

  public MultiModelMojoWriter() {}

  public MultiModelMojoWriter(M model) {
    super(model);
  }

  protected abstract List<Model> getSubModels();

  protected abstract void writeParentModelData() throws IOException;

  protected final void writeModelData() throws IOException {
    List<Model> subModels = getSubModels();
    writekv("submodel_count", subModels.size());
    int modelNum = 0;
    for (Model model : subModels) {
      writekv("submodel_key_" + modelNum, model._key.toString());
      writekv("submodel_dir_" + modelNum, getZipDirectory(model));
      modelNum++;
    }
    writeParentModelData();
  }

  protected void writeTo(ZipOutputStream zos) throws IOException {
    super.writeTo(zos);
    for (Model model : getSubModels()) {
      String zipDir = getZipDirectory(model);
      ModelMojoWriter writer = model.getMojo();
      writer.writeTo(zos, zipDir);
    }
  }

  private static String getZipDirectory(Model m) {
    String algo = m._parms.algoName();
    String key = m._key.toString();
    return "models/" + algo + "/" + key + "/";
  }

}
