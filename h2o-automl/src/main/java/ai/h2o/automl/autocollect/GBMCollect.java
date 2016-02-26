package ai.h2o.automl.autocollect;

import hex.Model;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import static water.util.RandomUtils.getRNG;

public class GBMCollect extends Collector {

  public static int MAXDEPTH=10;
  public static int MAXNBINS=1000;
  public static int MAXNBINSCATS=1000;
  public static int MAXNTREES=50;
  public static int MAXNROWS=50;

  protected GBMModel.GBMParameters genParms(long seedSplit, int idFrame, int ncol, HashSet<String> configs) {
    String configID;
    GBMModel.GBMParameters p = new GBMModel.GBMParameters();
    HashMap<String, Object> config = new HashMap<>();
    config.put("idFrame", idFrame);
    config.put("SplitSeed", seedSplit);
    do {
      config.put("ntrees", p._ntrees = 1+getRNG(new Random().nextLong()).nextInt(MAXNTREES));
      config.put("max_depth", p._max_depth = 1+getRNG(new Random().nextLong()).nextInt(MAXDEPTH));
      config.put("min_rows", p._min_rows = 1+getRNG(new Random().nextLong()).nextInt(MAXNROWS));
      config.put("learn_rate", p._learn_rate = getRNG(new Random().nextLong()).nextFloat());
      config.put("sample_rate", p._sample_rate = getRNG(new Random().nextLong()).nextFloat());
      config.put("col_sample_rate", p._col_sample_rate = getRNG(new Random().nextLong()).nextFloat());
      config.put("col_sample_rate_per_tree", p._col_sample_rate_per_tree = getRNG(new Random().nextLong()).nextFloat());
      config.put("nbins", p._nbins = 10+getRNG(new Random().nextLong()).nextInt(MAXNBINS));
      config.put("nbins_cats", p._nbins_cats = 10+getRNG(new Random().nextLong()).nextInt(MAXNBINSCATS));
      config.put("ConfigID", configID = getConfigId(p, idFrame));
    } while(!isValidConfig(configID, configs));
    configs.add(configID);
    AutoCollect.pushMeta(config, config.keySet().toArray(new String[config.size()]), "GBMConfig", null);
    return p;
  }
  @Override protected String configId(Model.Parameters p, int idFrame) { return getConfigId((GBMModel.GBMParameters)p, idFrame); }
  @Override protected GBM makeModelBuilder(Model.Parameters p) { return new GBM((GBMModel.GBMParameters)p); }
  private static String getConfigId(GBMModel.GBMParameters p, int idFrame) {
    return "gbm_"+idFrame+"_"+p._ntrees+"_"+p._max_depth+"_"+
            p._min_rows+"_"+p._learn_rate+"_"+p._sample_rate+"_"+
            p._col_sample_rate+"_"+p._col_sample_rate_per_tree+"_"+
            p._nbins+"_"+p._nbins_cats;
  }
}
