package water.codegen.java;

import hex.deeplearning.DeepLearningModel;
import hex.kmeans.KMeansModel;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;

/**
 * Created by michal on 5/18/16.
 *
 * FIXME: remove
 */
public class POJOCodeGenFactory {

  public static POJOModelCodeGenerator generator(GBMModel model) {
    return new GBMModelPOJOCodeGen(model).build();
  }

  public static POJOModelCodeGenerator generator(DRFModel model) {
    return new DRFModelPOJOCodeGen(model).build();
  }

  public static POJOModelCodeGenerator generator(DeepLearningModel model) {
    return new DeepLearningModelPOJOCodeGen(model).build();
  }

  public static POJOModelCodeGenerator generator(KMeansModel model) {
    return new KMeansModelPOJOCodeGen(model).build();
  }
}
