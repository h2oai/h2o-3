package ai.h2o.automl.autocollect;

import hex.Model;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import static water.util.RandomUtils.getRNG;

public class DRFCollect extends Collector {

  public static int MAXDEPTH=50;
  public static int MAXNBINS=1000;
  public static int MAXNBINSCATS=1000;
  public static int MAXNTREES=50;
  public static int MAXNROWS=50;

  protected DRFModel.DRFParameters genParms(long seedSplit, int idFrame, int ncol, HashSet<String> configs) {
    String configID;
    DRFModel.DRFParameters p = new DRFModel.DRFParameters();
    HashMap<String, Object> config = new HashMap<>();
    config.put("idFrame", idFrame);
    config.put("SplitSeed", seedSplit);
    do {
      config.put("mtries", p._mtries = 1+getRNG(new Random().nextLong()).nextInt(ncol-1));
      config.put("sample_rate", p._sample_rate = getRNG(new Random().nextLong()).nextFloat());
      config.put("ntrees", p._ntrees = 1+getRNG(new Random().nextLong()).nextInt(MAXNTREES));
      config.put("max_depth", p._max_depth = 1+getRNG(new Random().nextLong()).nextInt(MAXDEPTH));
      config.put("min_rows", p._min_rows = 1+getRNG(new Random().nextLong()).nextInt(MAXNROWS));
      config.put("nbins", p._nbins = 10+getRNG(new Random().nextLong()).nextInt(MAXNBINS));
      config.put("nbins_cats", p._nbins_cats = 10+getRNG(new Random().nextLong()).nextInt(MAXNBINSCATS));
      config.put("ConfigID", configID = getConfigId(p,idFrame));
    } while(!isValidConfig(configID, configs));
    configs.add(configID);
    AutoCollect.pushMeta(config, config.keySet().toArray(new String[config.size()]), "RFConfig",null);
    return p;
  }
  @Override protected DRF makeModelBuilder(Model.Parameters p) { return new DRF((DRFModel.DRFParameters)p); }
  @Override protected String configId(Model.Parameters p, int idFrame) { return getConfigId((DRFModel.DRFParameters)p,idFrame); }
  private static String getConfigId(DRFModel.DRFParameters p, int idFrame) {
    return "rf_" + idFrame + "_" + p._mtries + "_" + p._sample_rate +
            "_"  + p._ntrees + "_" + p._max_depth + "_" + p._min_rows +
            "_"  + p._nbins + "_" + p._nbins_cats;
  }
}
