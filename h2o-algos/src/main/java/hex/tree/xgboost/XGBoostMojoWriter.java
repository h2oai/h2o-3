package hex.tree.xgboost;

import hex.ModelMojoWriter;

import java.io.IOException;

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
    writeblob("boosterBytes", this.model._output._boosterBytes);
  }
}
