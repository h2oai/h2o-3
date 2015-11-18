package hex.api;

import hex.ModelBuilder;
import hex.deeplearning.DeepLearning;
import hex.glm.GLM;
import hex.glrm.GLRM;
import hex.kmeans.KMeans;
import hex.naivebayes.NaiveBayes;
import hex.pca.PCA;
import hex.svd.SVD;
import hex.tree.drf.DRF;
import hex.tree.gbm.GBM;
import water.H2O;
import water.api.AbstractRegister;

public class Register extends AbstractRegister {
  @Override
  public void register(String relativeResourcePath) {
    /////////////////////////////////////////////////////////////////////////////////////////////
    // Register the algorithms and their builder handlers:
    ModelBuilder.registerModelBuilder("gbm", "Gradient Boosting Machine", GBM.class);
    H2O.registerPOST("/3/ModelBuilders/gbm", GBMBuilderHandler.class, "train", "Train a GBM model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/gbm/parameters", GBMBuilderHandler.class, "validate_parameters", "Validate a set of GBM model builder parameters.");

    ModelBuilder.registerModelBuilder("drf", "Distributed RF", DRF.class);
    H2O.registerPOST("/3/ModelBuilders/drf", DRFBuilderHandler.class, "train",                                                        "Train a DRF model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/drf/parameters", DRFBuilderHandler.class, "validate_parameters", "Validate a set of DRF model builder parameters.");

    ModelBuilder.registerModelBuilder("kmeans", "K-means", KMeans.class);
    H2O.registerPOST("/3/ModelBuilders/kmeans", KMeansBuilderHandler.class, "train",                                                  "Train a KMeans model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/kmeans/parameters", KMeansBuilderHandler.class, "validate_parameters", "Validate a set of KMeans model builder parameters.");

    ModelBuilder.registerModelBuilder("deeplearning", "Deep Learning", DeepLearning.class);
    H2O.registerPOST("/3/ModelBuilders/deeplearning", DeepLearningBuilderHandler.class, "train",                                      "Train a Deep Learning model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/deeplearning/parameters", DeepLearningBuilderHandler.class, "validate_parameters", "Validate a set of Deep Learning model builder parameters.");

    ModelBuilder.registerModelBuilder("glm", "Generalized Linear Model", GLM.class);
    H2O.registerPOST("/3/ModelBuilders/glm", GLMBuilderHandler.class, "train",                                                        "Train a GLM model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/glm/parameters", GLMBuilderHandler.class, "validate_parameters",                               "Validate a set of GLM model builder parameters.");
    H2O.registerPOST("/3/MakeGLMModel", MakeGLMModelHandler.class, "make_model", "make a new GLM model based on existing one");

    ModelBuilder.registerModelBuilder("naivebayes", "Naive Bayes", NaiveBayes.class);
    H2O.registerPOST("/3/ModelBuilders/naivebayes", NaiveBayesBuilderHandler.class, "train",                                          "Train a Naive Bayes model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/naivebayes/parameters", NaiveBayesBuilderHandler.class, "validate_parameters", "Validate a set of Naive Bayes model builder parameters.");

    ModelBuilder.registerModelBuilder("pca", "Principal Component Analysis", PCA.class);
    H2O.registerPOST("/3/ModelBuilders/pca", PCABuilderHandler.class, "train",                                                        "Train a PCA model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/pca/parameters", PCABuilderHandler.class, "validate_parameters", "Validate a set of PCA model builder parameters.");

    ModelBuilder.registerModelBuilder("glrm", "Beta - Generalized Low Rank Model", GLRM.class);
    H2O.registerPOST("/3/ModelBuilders/glrm", GLRMBuilderHandler.class, "train",                                                     "Train a GLRM model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/glrm/parameters", GLRMBuilderHandler.class, "validate_parameters", "Validate a set of GLRM model builder parameters.");

    ModelBuilder.registerModelBuilder("svd", "Beta - Singular Value Decomposition", SVD.class);
    H2O.registerPOST("/99/ModelBuilders/svd", SVDBuilderHandler.class, "train",                                                        "Train a SVD model on the specified Frame.");
    H2O.registerPOST("/99/ModelBuilders/svd/parameters", SVDBuilderHandler.class, "validate_parameters",                               "Validate a set of SVD model builder parameters.");

    // Grid search is experimental feature
    H2O.registerPOST("/99/Grid/glm", GLMGridSearchHandler.class, "train",                                                            "Run grid search for GLRM model.");
    H2O.registerPOST("/99/Grid/gbm", GBMGridSearchHandler.class, "train",                                                              "Run grid search for GBM model.");
    H2O.registerPOST("/99/Grid/drf", DRFGridSearchHandler.class, "train",                                                              "Run grid search for DRF model.");
    H2O.registerPOST("/99/Grid/kmeans", KMeansGridSearchHandler.class, "train",                                                        "Run grid search for KMeans model.");
    H2O.registerPOST("/99/Grid/deeplearning", DeepLearningGridSearchHandler.class, "train",                                            "Run grid search for DeepLearning model.");
    H2O.registerPOST("/99/Grid/glrm", GLRMGridSearchHandler.class, "train",                                                            "Run grid search for GLRM model.");
    H2O.registerPOST("/99/Grid/pca", PCAGridSearchHandler.class, "train",                                                              "Run grid search for PCA model.");
    H2O.registerPOST("/99/Grid/svd", SVDGridSearchHandler.class, "train",                                                              "Run grid search for SVD model.");
    H2O.registerPOST("/99/Grid/naivebayes", NaiveBayesGridSearchHandler.class, "train",                                                "Run grid search for Naive Bayes model.");

    // ModelBuilder.registerModelBuilder("word2vec", Word2Vec.class);
    // H2O.registerPOST("/3/ModelBuilders/word2vec", Word2VecBuilderHandler.class, "train",                                              "Train a Word2Vec model on the specified Frame.");
    // H2O.registerPOST("/3/ModelBuilders/word2vec/parameters", Word2VecBuilderHandler.class, "validate_parameters",                     "Validate a set of Word2Vec model builder parameters.");

    // ModelBuilder.registerModelBuilder("example", Example.class);
    // H2O.registerPOST("/3/ModelBuilders/example", ExampleBuilderHandler.class, "train",                                                "Train an Example model on the specified Frame.");
    // H2O.registerPOST("/3/ModelBuilders/example/parameters", ExampleBuilderHandler.class, "validate_parameters",                       "Validate a set of Example model builder parameters.");

    // ModelBuilder.registerModelBuilder("grep", Grep.class);
    // H2O.registerPOST("/3/ModelBuilders/grep", GrepBuilderHandler.class, "train",                                                      "Search a raw text file for matches");
    // H2O.registerPOST("/3/ModelBuilders/grep/parameters", GrepBuilderHandler.class, "validate_parameters",                             "Validate a set of Grep parameters.");
  }
}
