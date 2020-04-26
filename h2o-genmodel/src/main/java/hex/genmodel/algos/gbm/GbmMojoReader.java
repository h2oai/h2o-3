package hex.genmodel.algos.gbm;

import hex.genmodel.algos.tree.SharedTreeMojoReader;
import hex.genmodel.utils.DistributionFamily;
import hex.genmodel.utils.LinkFunctionType;

import java.io.IOException;

/**
 */
public class GbmMojoReader extends SharedTreeMojoReader<GbmMojoModel> {

  @Override
  public String getModelName() {
    return "Gradient Boosting Machine";
  }

  @Override
  protected void readModelData() throws IOException {
    super.readModelData();
    _model._family = DistributionFamily.valueOf((String)readkv("distribution"));
    _model._init_f = readkv("init_f");
    _model._link_function = readLinkFunction();
  }

  private LinkFunctionType readLinkFunction() {
    String linkFunctionTypeName = readkv("link_function");
    if (linkFunctionTypeName != null)
      return LinkFunctionType.valueOf(linkFunctionTypeName);
    return defaultLinkFunction(_model._family);
  }
  
  private LinkFunctionType defaultLinkFunction(DistributionFamily family){
    switch (family) {
      case bernoulli:
      case quasibinomial:
      case modified_huber:
      case ordinal:
        return LinkFunctionType.logit;
      case multinomial:
      case poisson:
      case gamma:
      case tweedie:
        return LinkFunctionType.log;
      default:
        return LinkFunctionType.identity;
    }
  }

  @Override
  protected GbmMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new GbmMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() {
    return "1.40";
  }
}
