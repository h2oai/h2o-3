package ai.h2o.automl.hpsearch;

import hex.splitframe.ShuffleSplitFrame;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.TwoDimTable;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.frameRowAsArray;

public class RFSurrogateModel extends SurrogateModel{
  
  @Override
  public Frame evaluate(Frame hyperparameters, Frame train) {
    DRFModel.DRFParameters parms = new DRFModel.DRFParameters();

    //TODO check that validation frame is being used at all
    // or maybe it is faster to append to train and valid over time (lifelong learning would be useful here)
    if(train.numRows() > 5) {
      Frame[] splits = splitByRatio(train, 0.7, 0.3, 1234); // Splitting with different seeds on each attempt seems like hurts performance
      Frame trainSplit = splits[0];
      Frame validSplit = splits[1];
      parms._train = trainSplit._key;
      parms._valid = validSplit._key;
    } else
      parms._train = train._key;
    
    parms._response_column = "score";
    parms._nbins = 20;
    parms._nbins_cats = 1024;
    parms._ntrees = 70;
    parms._score_tree_interval = 50;
    parms._max_depth = 5;
    parms._mtries = -1;
    parms._sample_rate = 0.632f;
    parms._min_rows = 1;
    parms._seed = 12;
    parms._ignored_columns = new String[] {"id"};
    parms._ignore_const_cols = false; //TODO should we disable it?
    parms._build_tree_one_node = true;
    // Note: we probably don't want to use early stopping as we will have to score more frequently and it might prevent us from saving time
    //    parms._stopping_rounds = 5;
    //    parms._stopping_metric = ScoreKeeper.StoppingMetric.RMSE;
    //    parms._stopping_tolerance = ;

    DRFModel drf = new DRF(parms).trainModel().get();
    Log.info("RFSurrogateModel: Training set RMSE:   " + drf._output._training_metrics.rmse());
    drf._lastScoringPerTreePredictionsMap.clear(); //TODO who is also writing into this map?
    Frame predictions = drf.score(hyperparameters);
    /*double[] variances = new double[(int)hyperparameters.numRows()];

    int instanceIdx = 0;
    for(Map.Entry<Integer, Double[]> entry: drf._lastScoringPerTreePredictionsMap.entrySet()) { 
      variances[instanceIdx] = ArrayUtils.variance(entry.getValue());
      
      double[] data = frameRowAsArray(hyperparameters, instanceIdx, new String[]{"id"});
      
      int key = ArrayUtils.hashArray(data);
      if(!drf._lastScoringPerTreePredictionsMap.containsKey(key))
        System.out.println("Key not found");

      instanceIdx++;
    }*/

    double[] variances2 = new double[(int)hyperparameters.numRows()];
    for(int idx = 0; idx < hyperparameters.numRows(); idx++) {
      double[] data = frameRowAsArray(hyperparameters, idx, new String[]{"id"});

      int key = ArrayUtils.hashArray(data);
      Double[] perTreePredictions = drf._lastScoringPerTreePredictionsMap.get(key);
      variances2[idx] = ArrayUtils.variance(perTreePredictions);
    }

//    boolean equals = Arrays.equals(variances, variances2);

    assert drf._lastScoringPerTreePredictionsMap.size() == hyperparameters.numRows(); // there should not be any hash collisions for our grid-frame
    drf._lastScoringPerTreePredictionsMap.clear();
    drf.delete();
    
    // TODO make sure that predictions(means) are aligned with variance values
    hyperparameters.add("prediction", predictions.vec("predict"));
    hyperparameters.add("variance", Vec.makeVec(variances2, Vec.newKey()));
    
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
