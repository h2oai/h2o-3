package ai.h2o.automl.strategies.initial;

import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import water.Key;

public class InitModel {

  public static final byte REGRESSION =0;
  public static final byte BINOMIAL   =1;
  public static final byte MULTINOMIAL=2;

  public static DRF initDRF(String modelName, int ntree, int max_depth,
                            int min_rows, int stopping_rounds, double stopping_tolerance,
                            int nbins, int nbins_cats, int mtries, float sample_rate, long seed) {
    DRFModel.DRFParameters drf = new DRFModel.DRFParameters();
    drf._model_id = Key.make(modelName);
    drf._ntrees = ntree;
    drf._max_depth = max_depth;
    drf._min_rows = min_rows;
    drf._stopping_rounds = stopping_rounds;
    drf._stopping_tolerance = stopping_tolerance;
    drf._nbins = nbins;
    drf._nbins_cats = nbins_cats;
    drf._mtries = mtries;
    drf._sample_rate = sample_rate;
    drf._seed = seed;
    return new DRF(drf);
  }
}