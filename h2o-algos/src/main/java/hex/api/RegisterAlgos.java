package hex.api;

import hex.ModelBuilder;
import water.http.RequestServer;
import water.http.handlers.GridSearchHandler;

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
      new hex.deepwater   .DeepWater   (true),
    };
    // "Word2Vec", "Example", "Grep"
    for (ModelBuilder algo : algos) {
      String base = algo.getClass().getSimpleName();
      String lbase = base.toLowerCase();
      Class<? extends water.http.handlers.Handler> bh_clz = water.http.handlers.ModelBuilderHandler.class;
      int version = 3;
      if( base.equals("SVD") ) version = 99;  // SVD is experimental still
      if( base.equals("Aggregator") ) version = 99;  // Aggregator is experimental still

      register("POST /"+version+"/ModelBuilders/"+lbase, bh_clz, "train",
          "train_" + lbase,
          "Train a " + base + " model.");

      register("POST /"+version+"/ModelBuilders/"+lbase+"/parameters", bh_clz, "validate_parameters",
          "validate_" + lbase,
          "Validate a set of " + base + " model builder parameters.");

      // Grid search is experimental feature
      register("POST /99/Grid/"+lbase, GridSearchHandler.class, "train",
          "grid_search_" + lbase,
          "Run grid search for "+base+" model.");
    }

    register("POST /3/MakeGLMModel", MakeGLMModelHandler.class, "make_model", "make_glm_model",
        "Make a new GLM model based on existing one");

    register("GET /3/GetGLMRegPath", MakeGLMModelHandler.class, "extractRegularizationPath",
        "glm_regularization_path",
        "Get full regularization path");

    register("GET /3/ComputeGram", MakeGLMModelHandler.class, "computeGram",
        "weighted_gram_matrix",
        "Get weighted gram matrix");

    register("POST /3/DataInfoFrame",MakeGLMModelHandler.class, "getDataInfoFrame", "glm_datainfo_frame",
        "Test only");
  }

  static public void register(
          String method_url, Class<? extends water.http.handlers.Handler> hclass, String method, String apiName, String summary
  ) {
    RequestServer.registerEndpoint(apiName, method_url, hclass, method, summary);
  }
}
