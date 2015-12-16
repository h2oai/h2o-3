package ai.h2o.automl.strategies.initial;

import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import water.Key;
import water.fvec.Frame;

public class InitModel {

  public static final byte REGRESSION =0;
  public static final byte BINOMIAL   =1;
  public static final byte MULTINOMIAL=2;

  public static DRF initRF(Frame training_frame, String response) {
    return makeRF(
            Key.make("initDRF" + Key.make().toString().substring(0,5)).toString() /*name*/,
            training_frame,
            response,
            5 /*ntree*/,
            20 /*depth*/,
            10 /*minrows*/,

            // https://0xdata.atlassian.net/browse/STEAM-44
            1 /*stopping rounds*/,
            0.01 /*stopping tolerance*/,

            500 /*nbins*/,
            20 /*nbins_cats*/,
            -1 /*mtries*/,
            0.667f /*sample_rate*/,
            -1 /*seed*/);
  }
  private static DRF makeRF(String modelName, Frame training_frame, String response, int ntree,
                            int max_depth, int min_rows, int stopping_rounds, double stopping_tolerance,
                            int nbins, int nbins_cats, int mtries, float sample_rate, long seed) {
    DRFModel.DRFParameters drf = new DRFModel.DRFParameters();
    drf._train = training_frame._key;
    drf._response_column = response;
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