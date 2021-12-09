package hex.kmeans;

import hex.ModelMetrics;
import hex.ModelMojoWriter;

import java.io.IOException;

public class KMeansMojoWriter extends ModelMojoWriter<KMeansModel, KMeansModel.KMeansParameters, KMeansModel.KMeansOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public KMeansMojoWriter() {}

  public KMeansMojoWriter(KMeansModel model) {
    super(model);
  }

  @Override
  public String mojoVersion() {
    return "1.00";
  }

  @Override
  protected void writeModelData() throws IOException {
    writekv("standardize", model._parms._standardize);
    double[][] centers;
    if (model._parms._standardize) {
      writekv("standardize_means", model._output._normSub);
      writekv("standardize_mults", model._output._normMul);
      writekv("standardize_modes", model._output._mode);
      centers = model._output._centers_std_raw;
    } else
      centers = model._output._centers_raw;
    writekv("center_num", centers.length);
    for (int i = 0; i < centers.length; i++)
      writekv("center_" + i, centers[i]);
  }

  @Override
  public ModelMetrics.MetricBuilderFactory getModelBuilderFactory() {
    return new KMeansMetricBuilderFactory();
  }

}
