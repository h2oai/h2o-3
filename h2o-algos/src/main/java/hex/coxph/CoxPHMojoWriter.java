package hex.coxph;

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
    writekv2D_doubles("x_mean_cat", model._output._x_mean_cat);
    writekv2D_doubles("x_mean_num", model._output._x_mean_num);
    writekv("coef", model._output._coef);
    writeStrata();
    writeCoefIndexes();
  }

  private void writeCoefIndexes() throws IOException {
    final List<String> namesInList = Arrays.asList(model._output._names);
    final int[] _coef_indexes = new int[model._output._coef_names.length];
    int i = 0;
    for (String coefName : model._output._coef_names) {
      Integer index = namesInList.indexOf(coefName);
      _coef_indexes[i++] = index;
    }
    writekv("coef_indexes", _coef_indexes);
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
