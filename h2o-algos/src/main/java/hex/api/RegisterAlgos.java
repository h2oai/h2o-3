package hex.api;

import hex.ModelBuilder;
import hex.tree.TreeHandler;
import water.api.AlgoAbstractRegister;
import water.api.RestApiContext;
import water.api.SchemaServer;

public class RegisterAlgos extends AlgoAbstractRegister {

  @Override
  public void registerEndPoints(RestApiContext context) {
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
            new hex.tree.isofor .IsolationForest(true),
            new hex.aggregator  .Aggregator  (true),
            new hex.deepwater   .DeepWater   (true),
            new hex.word2vec    .Word2Vec    (true),
            new hex.ensemble    .StackedEnsemble(true),
            new hex.coxph       .CoxPH       (true),
    };

    // "Word2Vec", "Example", "Grep"
    for (ModelBuilder algo : algos) {
      String base = algo.getClass().getSimpleName();
      int version = SchemaServer.getStableVersion();
      if ( base.equals("SVD") ||
              base.equals("Aggregator") ||
              base.equals("StackedEnsemble")) {
        version = SchemaServer.getExperimentalVersion();
      }
      registerModelBuilder(context, algo, version);
    }

    context.registerEndpoint("make_glm_model", "POST /3/MakeGLMModel",
            MakeGLMModelHandler.class, "make_model",
            "Make a new GLM model based on existing one");

    context.registerEndpoint("glm_regularization_path","GET /3/GetGLMRegPath", MakeGLMModelHandler.class, "extractRegularizationPath",
            "Get full regularization path");

    context.registerEndpoint("weighted_gram_matrix", "GET /3/ComputeGram", MakeGLMModelHandler.class, "computeGram",
            "Get weighted gram matrix");

    context.registerEndpoint("word2vec_synonyms", "GET /3/Word2VecSynonyms", Word2VecHandler.class, "findSynonyms",
            "Find synonyms using a word2vec model");


    context.registerEndpoint("word2vec_transform", "GET /3/Word2VecTransform", Word2VecHandler.class, "transform",
            "Transform words to vectors using a word2vec model");

    context.registerEndpoint("glm_datainfo_frame", "POST /3/DataInfoFrame",MakeGLMModelHandler.class, "getDataInfoFrame",
            "Test only" );

    context.registerEndpoint("get_tree", "GET /3/Tree", TreeHandler.class, "getTree", "Obtain a traverseable representation of a specific tree");
  }

  @Override
  public String getName() {
    return "Algos";
  }
}
