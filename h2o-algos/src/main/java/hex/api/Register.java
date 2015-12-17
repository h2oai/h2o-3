package hex.api;

import hex.schemas.GBMV3;
import hex.tree.gbm.GBM;
import water.H2O;
import water.api.ModelBuilderHandler;

public class Register extends water.api.AbstractRegister {
  // Register the algorithms and their builder handlers:
  @Override public void register(String relativeResourcePath) throws ClassNotFoundException {
    String[] algos = new String[]{"GBM", "DRF", "KMeans", "DeepLearning", "GLM", "NaiveBayes", "PCA", "GLRM", "SVD"};
    // "Word2Vec", "Example", "Grep"
    hex.ModelBuilder.registerModelBuilders(algos);
    for( String algo : algos ) {
      Class bh_clz = Class.forName("hex.api."+algo+"BuilderHandler");
      H2O.registerPOST("/3/ModelBuilders/"+algo.toLowerCase()              , bh_clz, "train"              , "Train a "          +algo+" model.");
      H2O.registerPOST("/3/ModelBuilders/"+algo.toLowerCase()+"/parameters", bh_clz, "validate_parameters", "Validate a set of "+algo+" model builder parameters.");
      // Grid search is experimental feature
      H2O.registerPOST("/99/Grid/"+algo.toLowerCase(), GridSearchHandler.class, "train", "Run grid search for "+algo+" model.");
    }
  }
}

class GBMBuilderHandler extends ModelBuilderHandler<GBM, GBMV3, GBMV3.GBMParametersV3> { public GBMBuilderHandler() {} }
