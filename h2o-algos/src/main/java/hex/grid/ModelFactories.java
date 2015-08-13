package hex.grid;

import hex.ModelBuilder;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningParameters;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.glrm.GLRM;
import hex.glrm.GLRMModel;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import hex.naivebayes.NaiveBayes;
import hex.naivebayes.NaiveBayesModel;
import hex.pca.PCA;
import hex.pca.PCAModel;
import hex.svd.SVD;
import hex.svd.SVDModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;

/**
 * Static list of available model factories which are used for grid search.
 *
 * Each model factory provide a model builder to build a model based on given parameters.
 *
 * Note: can be replaced by reflection in the future.
 *
 * FIXME: should be removed - see /ModelBuilders/Algo meta end-point.
 */
public class ModelFactories {

  public static ModelFactory<GBMModel.GBMParameters>
      GBM_MODEL_FACTORY =
      new ModelFactory<GBMModel.GBMParameters>() {
        @Override
        public String getModelName() {
          return "GBM";
        }

        @Override
        public ModelBuilder buildModel(GBMModel.GBMParameters params) {
          return new GBM(params);
        }
      };

  public static ModelFactory<DRFModel.DRFParameters>
      DRF_MODEL_FACTORY =
      new ModelFactory<DRFModel.DRFParameters>() {
        @Override
        public String getModelName() {
          return "DRF";
        }

        @Override
        public ModelBuilder buildModel(DRFModel.DRFParameters params) {
          return new DRF(params);
        }
      };

  public static ModelFactory<KMeansModel.KMeansParameters>
      KMEANS_MODEL_FACTORY =
      new ModelFactory<KMeansModel.KMeansParameters>() {
        @Override
        public String getModelName() {
          return "Kmeans";
        }

        @Override
        public ModelBuilder buildModel(KMeansModel.KMeansParameters params) {
          return new KMeans(params);
        }
      };

  public static ModelFactory<DeepLearningParameters>
      DEEP_LEARNING_MODEL_FACTORY =
      new ModelFactory<DeepLearningParameters>() {
        @Override
        public String getModelName() {
          return "DeepLearning";
        }

        @Override
        public ModelBuilder buildModel(DeepLearningParameters params) {
          return new DeepLearning(params);
        }
      };

  public static ModelFactory<GLRMModel.GLRMParameters>
      GLRM_MODEL_FACTORY =
      new ModelFactory<GLRMModel.GLRMParameters>() {
        @Override
        public String getModelName() {
          return "GLRM";
        }

        @Override
        public ModelBuilder buildModel(GLRMModel.GLRMParameters params) {
          return new GLRM(params);
        }
      };

  public static ModelFactory<GLMModel.GLMParameters>
      GLM_MODEL_FACTORY =
      new ModelFactory<GLMModel.GLMParameters>() {
        @Override
        public String getModelName() {
          return "GLM";
        }

        @Override
        public ModelBuilder buildModel(GLMModel.GLMParameters params) {
          return new GLM(params);
        }
      };

  public static ModelFactory<PCAModel.PCAParameters>
      PCA_MODEL_FACTORY =
      new ModelFactory<PCAModel.PCAParameters>() {
        @Override
        public String getModelName() {
          return "PCA";
        }

        @Override
        public ModelBuilder buildModel(PCAModel.PCAParameters params) {
          return new PCA(params);
        }
      };

  public static ModelFactory<SVDModel.SVDParameters>
      SVD_MODEL_FACTORY =
      new ModelFactory<SVDModel.SVDParameters>() {
        @Override
        public String getModelName() {
          return "SVD";
        }

        @Override
        public ModelBuilder buildModel(SVDModel.SVDParameters params) {
          return new SVD(params);
        }
      };

  public static ModelFactory<NaiveBayesModel.NaiveBayesParameters>
      NAIVE_BAYES_MODEL_FACTORY =
      new ModelFactory<NaiveBayesModel.NaiveBayesParameters>() {
        @Override
        public String getModelName() {
          return "NaiveBayes";
        }

        @Override
        public ModelBuilder buildModel(NaiveBayesModel.NaiveBayesParameters params) {
          return new NaiveBayes(params);
        }
      };
}
