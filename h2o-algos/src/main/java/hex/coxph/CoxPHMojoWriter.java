package hex.coxph;

import hex.ModelMojoWriter;
import org.apache.commons.lang.ArrayUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;

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
    writekv("strata_count", model._output._strataMap.size());
    Stream<Integer> indexesStream = Arrays.stream(model._output._coef_names).map(n -> ArrayUtils.indexOf(model._output._names, n));
    writekv("coef_indexes", indexesStream.mapToInt(i -> i).toArray());
  }

  private void writekv2D_doubles(String key, double[][] array) throws IOException {
    assert null != key;
    writekv(key + "_num", array.length);
    for (int i = 0; i < array.length; i++) {
      System.out.println("Arrays.toString(array[i]) = " + Arrays.toString(array[i]));
      writekv(key + "_" + i, array[i]);
    }
  }

}
