package hex.api;

import water.H2O;
import hex.ModelBuilder;
import water.api.GridSearchHandler;

public class RegisterAlgos extends water.api.AbstractRegister {
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
    for (ModelBuilder algo : algos) {
      String base = algo.getClass().getSimpleName();
      String lbase = base.toLowerCase();
      Class<? extends water.api.Handler> bh_clz = water.api.ModelBuilderHandler.class;
      int version = 3;
      if( base.equals("SVD") ) version = 99;  // SVD is experimental still
      if( base.equals("Aggregator") ) version = 99;  // Aggregator is experimental still

      H2O.register("POST /"+version+"/ModelBuilders/"+lbase, bh_clz, "train",
          "train_" + lbase,
          "Train a " + base + " model.");

      H2O.register("POST /"+version+"/ModelBuilders/"+lbase+"/parameters", bh_clz, "validate_parameters",
          "validate_" + lbase,
          "Validate a set of " + base + " model builder parameters.");

      // Grid search is experimental feature
      H2O.register("POST /99/Grid/"+lbase, GridSearchHandler.class, "train",
          "grid_search_" + lbase,
          "Run grid search for "+base+" model.");
    }

    H2O.register("POST /3/MakeGLMModel", MakeGLMModelHandler.class, "make_model", "make_glm_model",
        "Make a new GLM model based on existing one");

    H2O.register("GET /3/GetGLMRegPath", MakeGLMModelHandler.class, "extractRegularizationPath",
        "glm_regularization_path",
        "Get full regularization path");

    H2O.register("POST /3/DataInfoFrame",MakeGLMModelHandler.class, "getDataInfoFrame", "glm_datainfo_frame",
        "Test only");
  }
}
