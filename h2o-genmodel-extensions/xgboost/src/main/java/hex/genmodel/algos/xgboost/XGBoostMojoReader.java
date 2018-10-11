package hex.genmodel.algos.xgboost;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;
import java.util.Arrays;

/**
 */
public class XGBoostMojoReader extends ModelMojoReader<XGBoostMojoModel> {

  @Override
  public String getModelName() {
    return "XGBoost";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._nums = readkv("nums");
    _model._cats = readkv("cats");
    _model._catOffsets = readkv("cat_offsets");
    _model._useAllFactorLevels = readkv("use_all_factor_levels");
    _model._sparse = readkv("sparse");
    if (exists("feature_map")) {
      final String[] featureMapTokens = new String(readblob("feature_map"), "UTF-8").split("\n");
      // There might be an empty line after "\n", this part avoids parsing empty token(s) at the end
      int nonEmptyTokenRange = featureMapTokens.length;
      for (int i = 0; i < featureMapTokens.length;i++) {
        if(featureMapTokens[i].trim().isEmpty()){
          nonEmptyTokenRange = i + 1;
          break;
        }
      }

      _model._featureMap = Arrays.copyOfRange(featureMapTokens, 0, nonEmptyTokenRange);
    }
    _model.postReadInit();
  }

  @Override
  protected XGBoostMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    byte[] boosterBytes;
    try {
      boosterBytes = readblob("boosterBytes");
    } catch (IOException e) {
      throw new IllegalStateException("MOJO is corrupted: cannot read the serialized Booster", e);
    }
    if (useJavaScoring()) {
      return new XGBoostJavaMojoModel(boosterBytes, columns, domains, responseColumn);
    } else {
      return new XGBoostNativeMojoModel(boosterBytes, columns, domains, responseColumn);
    }
  }

  public static boolean useJavaScoring() {
    return Boolean.getBoolean("sys.ai.h2o.xgboost.scoring.java.enable");
  }

}
