package hex.coxph;

import hex.ModelMetrics;
import hex.ModelMojoWriter;
import water.rapids.ast.prims.mungers.AstGroup;
import water.util.IcedHashMap;
import water.util.IcedInt;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CoxPHMojoWriter extends ModelMojoWriter<CoxPHModel, CoxPHModel.CoxPHParameters, CoxPHModel.CoxPHOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public CoxPHMojoWriter() {}

  public CoxPHMojoWriter(CoxPHModel model) {
    super(model);
  }

  @Override
  public String mojoVersion() {
    return "1.00";
  }

  @Override
  protected void writeModelData() throws IOException {
    writeRectangularDoubleArray(model._output._x_mean_cat, "x_mean_cat");
    writeRectangularDoubleArray(model._output._x_mean_num, "x_mean_num");
    writekv("coef", model._output._coef);
    writekv("cats", model._output.data_info._cats);
    writekv("cat_offsets", model._output.data_info._catOffsets);
    writekv("use_all_factor_levels", model._output.data_info._useAllFactorLevels);
    writeStrata();
  }

  @Override
  public ModelMetrics.MetricBuilderFactory getModelBuilderFactory() { return null; }

  private void writeStrata() throws IOException {
    final IcedHashMap<AstGroup.G, IcedInt> strataMap = model._output._strataMap;
    writekv("strata_count", strataMap.size());
    
    int strataNum = 0;
    for (AstGroup.G g : strataMap.keySet()) {
      writekv("strata_" + strataNum, g._gs);
      strataNum++;
    }
  }
}
