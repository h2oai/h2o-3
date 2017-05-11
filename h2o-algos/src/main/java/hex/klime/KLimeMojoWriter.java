package hex.klime;

import hex.Model;
import hex.MultiModelMojoWriter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class KLimeMojoWriter extends MultiModelMojoWriter<KLimeModel, KLimeModel.KLimeParameters, KLimeModel.KLimeOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public KLimeMojoWriter() {}

  public KLimeMojoWriter(KLimeModel model) {
    super(model);
  }

  @Override
  public String mojoVersion() {
    return "1.0";
  }

  @Override
  protected List<Model> getSubModels() {
    LinkedList<Model> subModels = new LinkedList<>();
    if (model._output._clustering != null)
      subModels.add(model._output._clustering);
    if (model._output._globalRegressionModel != null)
      subModels.add(model._output._globalRegressionModel);
    for (Model m : model._output._regressionModels)
      if (m != null)
        subModels.add(m);
    return subModels;
  }

  @Override
  protected void writeParentModelData() throws IOException {
    writekv("cluster_num", model._output._regressionModels.length);
    writekv("clustering_model", model._output._clustering._key);
    writekv("global_regression_model", model._output._globalRegressionModel._key);
    for (int i = 0; i < model._output._regressionModels.length; i++)
      if (model._output._regressionModels[i] != null)
        writekv("cluster_regression_model_" + i, model._output._regressionModels[i]._key);
  }

}
