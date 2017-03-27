package hex.api;

import water.H2O;
import hex.ModelBuilder;
import water.api.GridSearchHandler;
import water.api.SchemaServer;

public class RegisterAlgos extends water.api.AbstractRegister {
  // Register the algorithms and their builder handlers:
  @Override public void register(String relativeResourcePath) {
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
      new hex.word2vec    .Word2Vec    (true),
      new hex.ensemble    .StackedEnsemble(true),
    };
    
    // "Word2Vec", "Example", "Grep"
    for (ModelBuilder algo : algos) {
      String base = algo.getClass().getSimpleName();
      int version = 3; // FIXME: how to get the latest stable version?
      if ( base.equals("SVD") ||
           base.equals("Aggregator") ||
           base.equals("StackedEnsemble")) {
        version = SchemaServer.getExperimentalVersion();
      }
      registerModelBuilder(algo, version);
    }

    H2O.register("POST /3/MakeGLMModel", MakeGLMModelHandler.class, "make_model", "make_glm_model",
        "Make a new GLM model based on existing one");

    H2O.register("GET /3/GetGLMRegPath", MakeGLMModelHandler.class, "extractRegularizationPath",
        "glm_regularization_path",
        "Get full regularization path");

    H2O.register("GET /3/ComputeGram", MakeGLMModelHandler.class, "computeGram",
        "weighted_gram_matrix",
        "Get weighted gram matrix");

    H2O.register("GET /3/Word2VecSynonyms", Word2VecHandler.class, "findSynonyms", "word2vec_synonyms",
            "Find synonyms using a word2vec model");

    H2O.register("GET /3/Word2VecTransform", Word2VecHandler.class, "transform", "word2vec_transform",
            "Transform words to vectors using a word2vec model");

    H2O.register("POST /3/DataInfoFrame",MakeGLMModelHandler.class, "getDataInfoFrame", "glm_datainfo_frame",
        "Test only");
  }

  @Override
  public String getName() {
    return "Algos";
  }
}
