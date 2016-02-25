package ai.h2o.automl.autocollect;

import hex.Model;
import hex.ModelBuilder;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import static water.util.RandomUtils.getRNG;

public class DLCollect extends Collector{

  private static final int MAXNEURONS=2000; // maximum total number of neurons ( sum(neurons_per_layer) <= MAXNEURONS )
  private static final int MAXLAYERS=6;
  private static final int MAXEPOCHS=100;
//  private static final int MAXW2=10;        // only used when activation is Rectifier
  private static final double MAXL1=10;
  private static final double MAXL2=10;

  @Override protected Model.Parameters genParms(long seedSplit, int idFrame, int ncol, HashSet<String> configs) {
    String configID;
    DeepLearningModel.DeepLearningParameters p = new DeepLearningModel.DeepLearningParameters();
    HashMap<String, Object> config = new HashMap<>();
    DeepLearningModel.DeepLearningParameters.Activation[] vals = DeepLearningModel.DeepLearningParameters.Activation.values();
    config.put("idFrame", idFrame);
    config.put("SplitSeed", seedSplit);
    Random rng = getRNG(new Random().nextLong());
    do {
      int nlayer = 1+rng.nextInt(MAXLAYERS);
      int maxNeuronsPerLayer = MAXNEURONS / nlayer;
      config.put("activation", p._activation = vals[ rng.nextInt(vals.length) ]);
      config.put("layers", nlayer); p._hidden = new int[nlayer];
      Arrays.fill(p._hidden, 1+rng.nextInt(maxNeuronsPerLayer));
      config.put("neurons", p._hidden[0]);
      config.put("epochs",  p._epochs=1+rng.nextInt(MAXEPOCHS));
      config.put("input_dropout_ratio", p._input_dropout_ratio=rng.nextDouble());
      config.put("l1", p._l1=rng.nextDouble()*MAXL1);
      config.put("l2", p._l2=rng.nextDouble()*MAXL2);
      config.put("ConfigID", configID=getConfigId(p,idFrame));
    } while(!isValidConfig(configID,configs));
    configs.add(configID);
    AutoCollect.pushMeta(config, config.keySet().toArray(new String[config.size()]), "DLConfig",null);
    return p;
  }

  @Override protected ModelBuilder makeModelBuilder(Model.Parameters p) { return new DeepLearning((DeepLearningModel.DeepLearningParameters)p); }
  @Override protected String configId(Model.Parameters p, int idFrame) { return getConfigId((DeepLearningModel.DeepLearningParameters)p,idFrame); }
  private static String getConfigId(DeepLearningModel.DeepLearningParameters p, int idFrame) {
    return "dl_" + idFrame + "_" + p._activation + "_" + p._hidden.length +
            "_"  + p._hidden[0] + "_" + p._epochs + "_" + p._input_dropout_ratio +
            "_"  + p._l1 + "_" + p._l2;
  }
}