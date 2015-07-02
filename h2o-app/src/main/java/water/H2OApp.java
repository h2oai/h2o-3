
package water;

import hex.ModelBuilder;
import hex.api.*;
import hex.deeplearning.DeepLearning;
import hex.glm.GLM;
import hex.glrm.GLRM;
import hex.kmeans.KMeans;
import hex.naivebayes.NaiveBayes;
import hex.pca.PCA;
import hex.svd.SVD;
import hex.tree.drf.DRF;
import hex.tree.gbm.GBM;

import java.io.File;

public class H2OApp {
  public static void main2( String relpath ) { driver(new String[0],relpath); }

  public static void main( String[] args  ) { driver(args,System.getProperty("user.dir")); }

  private static void driver( String[] args, String relpath ) {

    // Fire up the H2O Cluster
    H2O.main(args);

    // Register REST API
    register(relpath);
  }

  static void register(String relpath) {

    H2O.registerResourceRoot(new File(relpath + File.separator + "h2o-web/src/main/resources/www"));
    H2O.registerResourceRoot(new File(relpath + File.separator + "h2o-core/src/main/resources/www"));

    /////////////////////////////////////////////////////////////////////////////////////////////
    // Register the algorithms and their builder handlers:
    ModelBuilder.registerModelBuilder("gbm", "Gradient Boosting Machine", GBM.class);
    H2O.registerPOST("/3/ModelBuilders/gbm", GBMBuilderHandler.class, "train",                                                        "Train a GBM model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/gbm/parameters", GBMBuilderHandler.class, "validate_parameters",                               "Validate a set of GBM model builder parameters.");

    ModelBuilder.registerModelBuilder("drf", "Distributed RF", DRF.class);
    H2O.registerPOST("/3/ModelBuilders/drf", DRFBuilderHandler.class, "train",                                                        "Train a DRF model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/drf/parameters", DRFBuilderHandler.class, "validate_parameters",                               "Validate a set of DRF model builder parameters.");

    ModelBuilder.registerModelBuilder("kmeans", "K-means", KMeans.class);
    H2O.registerPOST("/3/ModelBuilders/kmeans", KMeansBuilderHandler.class, "train",                                                  "Train a KMeans model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/kmeans/parameters", KMeansBuilderHandler.class, "validate_parameters",                         "Validate a set of KMeans model builder parameters.");

    ModelBuilder.registerModelBuilder("deeplearning", "Deep Learning", DeepLearning.class);
    H2O.registerPOST("/3/ModelBuilders/deeplearning", DeepLearningBuilderHandler.class, "train",                                      "Train a Deep Learning model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/deeplearning/parameters", DeepLearningBuilderHandler.class, "validate_parameters",             "Validate a set of Deep Learning model builder parameters.");

    ModelBuilder.registerModelBuilder("glm", "Generalized Linear Model", GLM.class);
    H2O.registerPOST("/3/ModelBuilders/glm", GLMBuilderHandler.class, "train",                                                        "Train a GLM model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/glm/parameters", GLMBuilderHandler.class, "validate_parameters",                               "Validate a set of GLM model builder parameters.");
    H2O.registerPOST("/3/MakeGLMModel", MakeGLMModelHandler.class, "make_model", "make a new GLM model based on existing one");

    ModelBuilder.registerModelBuilder("naivebayes", "Naive Bayes", NaiveBayes.class);
    H2O.registerPOST("/3/ModelBuilders/naivebayes", NaiveBayesBuilderHandler.class, "train",                                          "Train a Naive Bayes model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/naivebayes/parameters", NaiveBayesBuilderHandler.class, "validate_parameters",                 "Validate a set of Naive Bayes model builder parameters.");

    ModelBuilder.registerModelBuilder("glrm", "Beta - Generalized Low Rank Model", GLRM.class);
    H2O.registerPOST("/3/ModelBuilders/glrm", GLRMBuilderHandler.class, "train",                                                      "Train a GLRM model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/glrm/parameters", GLRMBuilderHandler.class, "validate_parameters",                             "Validate a set of GLRM model builder parameters.");

    ModelBuilder.registerModelBuilder("pca", "Beta - Principal Component Analysis", PCA.class);
    H2O.registerPOST("/3/ModelBuilders/pca", PCABuilderHandler.class, "train",                                                        "Train a PCA model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/pca/parameters", PCABuilderHandler.class, "validate_parameters",                               "Validate a set of PCA model builder parameters.");

    ModelBuilder.registerModelBuilder("svd", "Beta - Singular Value Decomposition", SVD.class);
    H2O.registerPOST("/3/ModelBuilders/svd", SVDBuilderHandler.class, "train",                                                        "Train a SVD model on the specified Frame.");
    H2O.registerPOST("/3/ModelBuilders/svd/parameters", SVDBuilderHandler.class, "validate_parameters",                               "Validate a set of SVD model builder parameters.");

    H2O.registerPOST("/3/Grid/gbm", GBMGridSearchHandler.class, "train",                                                              "Run grid search for GBM model.");
    H2O.registerPOST("/3/Grid/drf", DRFGridSearchHandler.class, "train",                                                              "Run grid search for DRF model.");
    H2O.registerPOST("/3/Grid/kmeans", KMeansGridSearchHandler.class, "train",                                                         "Run grid search for KMeans model.");

    // ModelBuilder.registerModelBuilder("word2vec", Word2Vec.class);
    // H2O.registerPOST("/3/ModelBuilders/word2vec", Word2VecBuilderHandler.class, "train",                                              "Train a Word2Vec model on the specified Frame.");
    // H2O.registerPOST("/3/ModelBuilders/word2vec/parameters", Word2VecBuilderHandler.class, "validate_parameters",                     "Validate a set of Word2Vec model builder parameters.");

    // ModelBuilder.registerModelBuilder("example", Example.class);
    // H2O.registerPOST("/3/ModelBuilders/example", ExampleBuilderHandler.class, "train",                                                "Train an Example model on the specified Frame.");
    // H2O.registerPOST("/3/ModelBuilders/example/parameters", ExampleBuilderHandler.class, "validate_parameters",                       "Validate a set of Example model builder parameters.");

    // ModelBuilder.registerModelBuilder("grep", Grep.class);
    // H2O.registerPOST("/3/ModelBuilders/grep", GrepBuilderHandler.class, "train",                                                      "Search a raw text file for matches");
    // H2O.registerPOST("/3/ModelBuilders/grep/parameters", GrepBuilderHandler.class, "validate_parameters",                             "Validate a set of Grep parameters.");

    // Done adding menu items; fire up web server
    H2O.finalizeRegistration();
  }
}
