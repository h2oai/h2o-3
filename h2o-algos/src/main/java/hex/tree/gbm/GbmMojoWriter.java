package hex.tree.gbm;

import hex.Distribution;
import hex.DistributionFactory;
import hex.ModelMetrics;
import hex.ModelMetricsSupervised;
import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.tree.SharedTreeMojoWriter;

import java.io.IOException;

/**
 * MOJO support for GBM model.
 */
public class GbmMojoWriter extends SharedTreeMojoWriter<GBMModel, GBMModel.GBMParameters, GBMModel.GBMOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public GbmMojoWriter() {}

  public GbmMojoWriter(GBMModel model) {
    super(model);
  }

  @Override public String mojoVersion() {
    return "1.40";
  }

  @Override
  protected void writeModelData() throws IOException {
    super.writeModelData();
    Distribution dist = DistributionFactory.getDistribution(model._parms);
    writekv("distribution", dist._family);
    writekv("link_function", dist._linkFunction.linkFunctionType);
    writekv("init_f", model._output._init_f);
  }

  @Override
  public ModelMetrics.MetricBuilderFactory getModelBuilderFactory() {
    return new ModelMetricsSupervised.SupervisedMetricBuilderFactory<GBMModel, GbmMojoModel>();
  }
}
