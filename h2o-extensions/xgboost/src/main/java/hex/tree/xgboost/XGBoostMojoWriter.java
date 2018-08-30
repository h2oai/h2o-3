package hex.tree.xgboost;

import hex.ModelMojoWriter;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * MOJO support for GBM model.
 */
public class XGBoostMojoWriter extends ModelMojoWriter<XGBoostModel, XGBoostModel.XGBoostParameters, XGBoostOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public XGBoostMojoWriter() {}

  public XGBoostMojoWriter(XGBoostModel model) {
    super(model);
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

  @Override
  protected void writeModelData() throws IOException {
    writeblob("boosterBytes", this.model.model_info()._boosterBytes);
    writekv("nums", model._output._nums);
    writekv("cats", model._output._cats);
    writekv("cat_offsets", model._output._catOffsets);
    writekv("use_all_factor_levels", model._output._useAllFactorLevels);
    writekv("sparse", model._output._sparse);
    writeblob("feature_map", model.model_info().getFeatureMap().getBytes(Charset.forName("UTF-8")));
  }
}
