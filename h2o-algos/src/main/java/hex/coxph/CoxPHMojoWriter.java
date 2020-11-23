package hex.coxph;

import hex.ModelMojoWriter;
import water.rapids.ast.prims.mungers.AstGroup;
import water.util.IcedHashMap;
import water.util.IcedInt;

import java.io.IOException;
import java.util.Arrays;

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
    writekv2D_doubles("x_mean_cat", model._output._x_mean_cat);
    writekv2D_doubles("x_mean_num", model._output._x_mean_num);
    writekv("coef", model._output._coef);
    writeStrata();
    writeCoefNames();
  }

  private void writeCoefNames() throws IOException {
    final String[] coef_names = model._output._coef_names;
    writekv("coef_names_num", coef_names.length);
    for (int i = 0; i < coef_names.length; i++) {
      writekv("coef_names_"+ i, coef_names[i]);
    }
  }

  private void writeStrata() throws IOException {
    final IcedHashMap<AstGroup.G, IcedInt> strataMap = model._output._strataMap;
    writekv("strata_count", strataMap.size());
    
    int strataNum = 0;
    for (AstGroup.G g : strataMap.keySet()) {
      writekv("strata_" + strataNum, g._gs);
      strataNum++;
    }
  }

  private void writekv2D_doubles(String key, double[][] array) throws IOException {
    assert null != key;
    writekv(key + "_num", array.length);
    for (int i = 0; i < array.length; i++) {
      writekv(key + "_" + i, array[i]);
    }
  }

}
