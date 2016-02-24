package ai.h2o.automl.autocollect;

import hex.Model;
import hex.ModelBuilder;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import water.H2O;

import java.util.HashSet;

public class DLCollect extends Collector{

  @Override protected Model.Parameters genParms(long seedSplit, int idFrame, int ncol, HashSet<String> configs) {
    throw H2O.unimpl();
  }

  //          "activation", "layers", "neurons", "epochs", "rho", "rate", "rate_annealing",
//    "rate_decay", "momentum_ramp", "momentum_stable", "input_dropout_ratio", "l1",
//            "l2", "max_w2", "initial_weight_distribution", "initial_weight_scale"
//    String configID;
//    DeepLearningModel.DeepLearningParameters p = new DeepLearningModel.DeepLearningParameters();
//    do {
//      p._activation = DeepLearningModel.DeepLearningParameters.Activation.values()[getRNG(new Random().nextLong()).nextInt(DeepLearningModel.DeepLearningParameters.Activation.values().length)];
//      p._hidden
//      configID = getConfig(p);
//    } while(!isValidConfig(configID));
//    configs.add(configID);
//    return p;
//  }


  private final static String[] DLGRIDDABLES = new String[]{
          "activation", "layers", "neurons", "epochs", "rho", "rate", "rate_annealing",
          "rate_decay", "momentum_ramp", "momentum_stable", "input_dropout_ratio", "l1",
          "l2", "max_w2", "initial_weight_distribution", "initial_weight_scale"
  };
  @Override protected ModelBuilder makeModelBuilder(Model.Parameters p) { return new DeepLearning((DeepLearningModel.DeepLearningParameters)p); }
  @Override protected String configId(Model.Parameters p, int idFrame) { return null; } // return getConfigId((DeepLearningModel.DeepLearningParameters)p, idFrame); }

}
