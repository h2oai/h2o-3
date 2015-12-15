package hex.api;

import water.H2O;

public class Register extends water.api.AbstractRegister {
  // Register the algorithms and their builder handlers:
  @Override public void register(String relativeResourcePath) {
    String[] algos = new String[]{"GBM", "DRF", "KMeans", "DeepLearning", "GLM", "NaiveBayes", "PCA", "GLRM", "SVD"};
    // "Word2Vec", "Example", "Grep"
    hex.ModelBuilder.registerModelBuilders(algos);
    for( String algo : algos ) {
      H2O.registerPOST("/3/ModelBuilders/"+algo.toLowerCase(), BuilderHandler.class, "train", "Train a "+algo+" model on the specified Frame.");
      H2O.registerPOST("/3/ModelBuilders/"+algo.toLowerCase()+"/parameters", BuilderHandler.class, "validate_parameters", "Validate a set of "+algo+" model builder parameters.");
      // Grid search is experimental feature
      H2O.registerPOST("/99/Grid/"+algo.toLowerCase(), GridSearchHandler.class, "train", "Run grid search for "+algo+" model.");
    }
  }
}
