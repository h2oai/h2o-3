package hex.genmodel.algos.xgboost;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;

/**
 */
public class XGBoostMojoReader extends ModelMojoReader<XGBoostMojoModel> {

  @Override
  protected void readModelData() throws IOException {
    //FIXME
  }

  @Override
  protected XGBoostMojoModel makeModel(String[] columns, String[][] domains) {
    return new XGBoostMojoModel(columns, domains);
  }
}
