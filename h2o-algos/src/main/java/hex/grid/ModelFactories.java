package hex.grid;

import hex.ModelBuilder;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningParameters;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;

/**
 * Static list of available model factories which
 * are used for grid search.
 *
 * Each model factory provide a model builder to build a model
 * based on given parameters.
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
}
