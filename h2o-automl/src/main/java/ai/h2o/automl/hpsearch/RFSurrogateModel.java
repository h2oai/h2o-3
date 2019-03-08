package ai.h2o.automl.hpsearch;

import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import water.fvec.Frame;
import water.util.Log;

import java.util.Random;

public class RFSurrogateModel extends SurrogateModel{
  
  @Override
  public Frame evaluate(Frame hyperparameters, Frame train, Frame valid) {
    DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
    parms._train = train._key; 
//    parms._valid = test.valid; // TODO think about proper validation frame
    parms._response_column = "score";
    parms._nbins = 20;
    parms._nbins_cats = 1024;
    parms._ntrees = 2;
//    parms._score_tree_interval = 5;
    parms._max_depth = 5;
    parms._mtries = -1;
    parms._sample_rate = 0.632f;
    parms._min_rows = 1;
    parms._seed = 12;
    parms._ignore_const_cols = false; //TODO should we disable it

    DRFModel drf = new DRF(parms).trainModel().get();
    Log.info("RFSurrogateModel: Training set AUC:   " + drf._output._training_metrics.rmse());
//    Log.info("RFSurrogateModel: Validation set AUC: " + drf._output._validation_metrics.auc_obj()._auc);
    Frame predictions = drf.score(hyperparameters);
    hyperparameters.add("prediction", predictions.vec("predict"));
    return hyperparameters;
  }
}
