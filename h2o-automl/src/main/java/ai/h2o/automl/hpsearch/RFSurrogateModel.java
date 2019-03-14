package ai.h2o.automl.hpsearch;

import hex.splitframe.ShuffleSplitFrame;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import water.Key;
import water.fvec.Frame;
import water.util.Log;

public class RFSurrogateModel extends SurrogateModel{
  
  @Override
  public Frame evaluate(Frame hyperparameters, Frame train) {
    DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
    
    // or maybe it is faster to append to train and valid over time (lifelong learning would be useful here)
    if(train.numRows() > 10) {
      Frame[] splits = splitByRatio(train, 0.7, 0.3, 1234);
      Frame trainSplit = splits[0];
      Frame validSplit = splits[1];
      parms._train = trainSplit._key;
      parms._valid = validSplit._key;
    } else
      parms._train = train._key;
    
    parms._response_column = "score";
    parms._nbins = 20;
    parms._nbins_cats = 1024;
    parms._ntrees = 100;
    parms._score_tree_interval = 100;
    parms._max_depth = 5;
    parms._mtries = -1;
    parms._sample_rate = 0.9f;
//    parms._col_sample_rate_per_tree = 0.8f;
    parms._min_rows = 1;
    parms._seed = 12;
    parms._ignored_columns = new String[] {"id"};
    parms._ignore_const_cols = false; //TODO should we disable it?

    DRFModel drf = new DRF(parms).trainModel().get();
    Log.info("RFSurrogateModel: Training set RMSE:   " + drf._output._training_metrics.rmse());
//    Log.info("RFSurrogateModel: Validation set AUC: " + drf._output._validation_metrics.auc_obj()._auc);
    
    Frame predictions = drf.score(hyperparameters);
    hyperparameters.add("prediction", predictions.vec("predict"));
    return hyperparameters;
  }

  private Frame[] splitByRatio(Frame frame, double first, double second, long seed) {
    double[] ratios = new double[]{first, second};
    Key<Frame>[] keys = new Key[] {Key.make(), Key.make()};
    Frame[] splits = null;
    splits = ShuffleSplitFrame.shuffleSplitFrame(frame, keys, ratios, seed);
    return splits;
  }
}
