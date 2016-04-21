package hex.api;

import water.H2O;
import hex.ModelBuilder;
import water.api.GridSearchHandler;

public class Register extends water.api.AbstractRegister {
  // Register the algorithms and their builder handlers:
  @Override public void register(String relativeResourcePath) throws ClassNotFoundException {
    // List of algorithms
    ModelBuilder[] algos = new ModelBuilder[]{
      new hex.deeplearning.DeepLearning(true),
      new hex.glm         .GLM         (true),
      new hex.glrm        .GLRM        (true),
      new hex.kmeans      .KMeans      (true),
      new hex.naivebayes  .NaiveBayes  (true),
      new hex.pca         .PCA         (true),
      new hex.svd         .SVD         (true),
      new hex.tree.drf    .DRF         (true),
      new hex.tree.gbm    .GBM         (true),
      new hex.aggregator  .Aggregator  (true),
    };
    // "Word2Vec", "Example", "Grep"
    for( ModelBuilder algo : algos ) {
      String base = algo.getClass().getSimpleName();
      String lbase = base.toLowerCase();
      Class bh_clz = water.api.ModelBuilderHandler.class;
      int version = 3;
      if( base.equals("SVD") ) version = 99;  // SVD is experimental still

      H2O.registerPOST("/"+version+"/ModelBuilders/"+lbase              , bh_clz, "train"              , "Train a "          +base+" model.");
      H2O.registerPOST("/"+version+"/ModelBuilders/"+lbase+"/parameters", bh_clz, "validate_parameters", "Validate a set of "+base+" model builder parameters.");
      // Grid search is experimental feature
      H2O.registerPOST("/99/Grid/"+lbase, GridSearchHandler.class, "train", "Run grid search for "+base+" model.");
    }
    H2O.registerPOST("/3/MakeGLMModel", MakeGLMModelHandler.class, "make_model", "make a new GLM model based on existing one");
    H2O.registerPOST("/3/DataInfoFrame",MakeGLMModelHandler.class, "getDataInfoFrame", "test only");
  }
}
