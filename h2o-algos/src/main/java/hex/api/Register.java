package hex.api;

import hex.deeplearning.DeepLearning;
import hex.glm.GLM;
import hex.glrm.GLRM;
import hex.kmeans.KMeans;
import hex.naivebayes.NaiveBayes;
import hex.pca.PCA;
import hex.schemas.*;
import hex.svd.SVD;
import hex.tree.drf.DRF;
import hex.tree.gbm.GBM;
import water.H2O;
import water.api.ModelBuilderHandler;

public class Register extends water.api.AbstractRegister {
  // Register the algorithms and their builder handlers:
  @Override public void register(String relativeResourcePath) throws ClassNotFoundException {
    // List of algorithms
    String[] algos = new String[]{
      "hex.deeplearning.DeepLearning", 
      "hex.glm.GLM", 
      "hex.glrm.GLRM", 
      "hex.kmeans.KMeans", 
      "hex.naivebayes.NaiveBayes", 
      "hex.pca.PCA", 
      "hex.svd.SVD",
      "hex.tree.drf.DRF", 
      "hex.tree.gbm.GBM", 
    };
    // "Word2Vec", "Example", "Grep"
    for( String algo : algos ) {
      String base = algo.substring(algo.lastIndexOf('.')+1);
      hex.ModelBuilder.registerModelBuilder(algo,base);
      Class bh_clz = Class.forName("hex.api."+base+"BuilderHandler");
      H2O.registerPOST("/3/ModelBuilders/"+base.toLowerCase()              , bh_clz, "train"              , "Train a "          +base+" model.");
      H2O.registerPOST("/3/ModelBuilders/"+base.toLowerCase()+"/parameters", bh_clz, "validate_parameters", "Validate a set of "+base+" model builder parameters.");
      // Grid search is experimental feature
      H2O.registerPOST("/99/Grid/"+base.toLowerCase(), GridSearchHandler.class, "train", "Run grid search for "+base+" model.");
    }
  }
}

@SuppressWarnings("unused") class DRFBuilderHandler extends ModelBuilderHandler<DRF, DRFV3, DRFV3.DRFParametersV3> { }
@SuppressWarnings("unused") class DeepLearningBuilderHandler extends ModelBuilderHandler<DeepLearning, DeepLearningV3, DeepLearningV3.DeepLearningParametersV3> { }
@SuppressWarnings("unused") class GBMBuilderHandler extends ModelBuilderHandler<GBM, GBMV3, GBMV3.GBMParametersV3> { }
@SuppressWarnings("unused") class GLMBuilderHandler extends ModelBuilderHandler<GLM, GLMV3, GLMV3.GLMParametersV3> { }
@SuppressWarnings("unused") class GLRMBuilderHandler extends ModelBuilderHandler<GLRM, GLRMV3, GLRMV3.GLRMParametersV3> { }
@SuppressWarnings("unused") class KMeansBuilderHandler extends ModelBuilderHandler<KMeans, KMeansV3, KMeansV3.KMeansParametersV3> { }
@SuppressWarnings("unused") class NaiveBayesBuilderHandler extends ModelBuilderHandler<NaiveBayes, NaiveBayesV3, NaiveBayesV3.NaiveBayesParametersV3> { }
@SuppressWarnings("unused") class PCABuilderHandler extends ModelBuilderHandler<PCA, PCAV3, PCAV3.PCAParametersV3> { }
@SuppressWarnings("unused") class SVDBuilderHandler extends ModelBuilderHandler<SVD, SVDV99, SVDV99.SVDParametersV99> { }
