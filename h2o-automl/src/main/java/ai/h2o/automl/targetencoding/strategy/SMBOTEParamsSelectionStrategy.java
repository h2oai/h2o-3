package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.hpsearch.RFSMBO;
import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import hex.ModelBuilder;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.*;

public class SMBOTEParamsSelectionStrategy extends GridBasedTEParamsSelectionStrategy {

  private Frame _leaderboardData;
  private String _responseColumn;
  private String[] _columnsToEncode; // we might want to search for subset as well
  private boolean _theBiggerTheBetter;

  private PriorityQueue<Evaluated<TargetEncodingParams>> _evaluatedQueue;
  private TargetEncodingHyperparamsEvaluator _evaluator = new TargetEncodingHyperparamsEvaluator();
//    private GridSearchTEStratifiedEvaluator _evaluator = new GridSearchTEStratifiedEvaluator();

  private double _earlyStoppingRatio;

  public SMBOTEParamsSelectionStrategy(Frame leaderboard, double earlyStoppingRatio, String responseColumn, String[] columnsToEncode, boolean theBiggerTheBetter, long seed) {
    _seed = seed;
    
    _leaderboardData = leaderboard;
    _earlyStoppingRatio = earlyStoppingRatio;
    _responseColumn = responseColumn;
    _columnsToEncode = columnsToEncode;
    _theBiggerTheBetter = theBiggerTheBetter;
    
    _evaluatedQueue = new PriorityQueue<>(10, new EvaluatedComparator(theBiggerTheBetter));
  }
  
  static class EarlyStopper {
    private int _numberOfAttemptsBeforeStopping;
    private double _initialThreshold;
    private int fruitlessAttemptsCount = 0;

    public EarlyStopper(int numberOfAttemptsBeforeStopping, double initialThreshold) {
      
      _numberOfAttemptsBeforeStopping = numberOfAttemptsBeforeStopping;
      _initialThreshold = initialThreshold;
    }

    public boolean proceed() {
      return fruitlessAttemptsCount < _numberOfAttemptsBeforeStopping;
    };

    // less or more is better
    public void update(double newValue) {
      if(newValue <= _initialThreshold) fruitlessAttemptsCount++;
      else {
        fruitlessAttemptsCount = 0;
        _initialThreshold = newValue;
      }
    };
  }
  
  @Override
  public TargetEncodingParams getBestParams(ModelBuilder modelBuilder) {
    return getBestParamsWithEvaluation(modelBuilder).getItem();
  }

  public Evaluated<TargetEncodingParams> getBestParamsWithEvaluation(ModelBuilder modelBuilder) {
    assert _modelValidationMode != null : "`setTESearchSpace()` method should has been called to setup appropriate hyperspace.";
    
    ArrayList<GridSearchTEParamsSelectionStrategy.GridEntry> wholeSpace = materialiseHyperspace();
    
    Exporter exporter = new Exporter();

    double thresholdScoreFromPriors = 0;
    
    int numberOfPriorEvals = 10;
    GridSearchTEParamsSelectionStrategy.GridEntry[] entriesForPrior = wholeSpace.subList(0, numberOfPriorEvals).toArray(new GridSearchTEParamsSelectionStrategy.GridEntry[0]);
    double[] priorScores = new double[numberOfPriorEvals];
    int priorIndex = 0;
    for(GridSearchTEParamsSelectionStrategy.GridEntry entry :entriesForPrior) {
      TargetEncodingParams param = new TargetEncodingParams(entry.getItem());

      ModelBuilder clonedModelBuilder = ModelBuilder.clone(modelBuilder);
      clonedModelBuilder.init(false); // in _evaluator we assume that init() has been already called
      double evaluationResult = _evaluator.evaluate(param, clonedModelBuilder, _modelValidationMode, _leaderboardData, getColumnsToEncode(), _seed);
      priorScores[priorIndex] = evaluationResult;
      _evaluatedQueue.add(new Evaluated<>(param, evaluationResult, priorIndex));

      priorIndex++;
      thresholdScoreFromPriors = Math.max(thresholdScoreFromPriors, evaluationResult);
      exporter.update(0.0, evaluationResult);
    }

    Frame priorHpsAsFrame = hyperspaceMapToFrame(entriesForPrior);
    priorHpsAsFrame.add("score", Vec.makeVec(priorScores, Vec.newKey()));
    Frame priorHpsWithScores = priorHpsAsFrame;

    GridSearchTEParamsSelectionStrategy.GridEntry[] unexploredHyperSpace = wholeSpace.subList(numberOfPriorEvals, wholeSpace.size()).toArray(new GridSearchTEParamsSelectionStrategy.GridEntry[0]);
    //TODO it should contain only undiscovered. We will need one more cache for already selected ones.
    Frame unexploredHyperspaceAsFrame = hyperspaceMapToFrame(unexploredHyperSpace);

    printOutFrameAsTable(unexploredHyperspaceAsFrame);

    RFSMBO rfsmbo = new RFSMBO(){};
    int numberOfAttemptsBeforeStopping = (int) (unexploredHyperspaceAsFrame.numRows() * _earlyStoppingRatio);
    EarlyStopper earlyStopper = new EarlyStopper(numberOfAttemptsBeforeStopping, thresholdScoreFromPriors);

    while(earlyStopper.proceed() && unexploredHyperspaceAsFrame.numRows() > 0 ) {
      
      if(rfsmbo.hasNoPrior()) {
        rfsmbo.updatePrior(priorHpsWithScores);
      }
      
      Frame suggestedHPs = rfsmbo.getNextBestHyperparameters(unexploredHyperspaceAsFrame);
      double idToRemove = suggestedHPs.vec(suggestedHPs.find("id")).at(0);
      unexploredHyperspaceAsFrame = TargetEncoderFrameHelper.filterNotByValue(unexploredHyperspaceAsFrame, unexploredHyperspaceAsFrame.find("id"), idToRemove);
      
      HashMap<String, Object> suggestedHPsAsMap = singleRowFrameToMap(suggestedHPs);
      TargetEncodingParams param = new TargetEncodingParams(suggestedHPsAsMap);

      final ModelBuilder clonedModelBuilder = ModelBuilder.clone(modelBuilder);
      clonedModelBuilder.init(false); // in _evaluator we assume that init() has been already called
      double evaluationResult = _evaluator.evaluate(param, clonedModelBuilder, _modelValidationMode, _leaderboardData, getColumnsToEncode(), _seed);
      
      earlyStopper.update(evaluationResult);
      
      printOutFrameAsTable(suggestedHPs);
      
      //Remove prediction from surrogate and add score on objective function
      exporter.update(suggestedHPs.vec(suggestedHPs.find("prediction")).at(0), evaluationResult);
      suggestedHPs.remove(suggestedHPs.find("prediction")).remove();
      suggestedHPs.add("score", Vec.makeCon(evaluationResult, 1));
      
      rfsmbo.updatePrior(suggestedHPs);

      int evaluationSequenceNumber = _evaluatedQueue.size();
      _evaluatedQueue.add(new Evaluated<>(param, evaluationResult, evaluationSequenceNumber));
    }

    exporter.exportToCSV(modelBuilder._parms.fullName());
    Evaluated<TargetEncodingParams> targetEncodingParamsEvaluated = _evaluatedQueue.peek();

    return targetEncodingParamsEvaluated;
  }

  static class Exporter {
    private ArrayList<Double> predictions = new ArrayList<>();
    private ArrayList<Double> scores = new ArrayList<>();

    public Exporter() {
    }

    public void exportToCSV(String modelName) {
      double[] scoresAsDouble = new double[scores.size()];
      for (int i = 0; i < scores.size(); i++) {
        scoresAsDouble[i] = (double) scores.toArray()[i];
      }
      Vec predVec = Vec.makeVec(scoresAsDouble, Vec.newKey());
      Frame fr = new Frame(new String[]{"score"}, new Vec[]{predVec});
      Frame.export(fr, "scores_smbo_" + modelName + "-" + System.currentTimeMillis() / 1000 + ".csv", "frame_name", true, 1).get();
    };

    public void update(double prediction, double score) {
      predictions.add(prediction);
      scores.add(score);
    };
  }

  private ArrayList<GridSearchTEParamsSelectionStrategy.GridEntry> materialiseHyperspace() {
    // We should not do it randomly
    ArrayList<GridSearchTEParamsSelectionStrategy.GridEntry> wholeSpace = new ArrayList<GridSearchTEParamsSelectionStrategy.GridEntry>();
    try {
      double entryIndex = 0;
      while (true) {
        GridSearchTEParamsSelectionStrategy.GridEntry selected = _randomSelector.getNext();
        selected.getItem().put("id", entryIndex);
        wholeSpace.add(selected);
        entryIndex++;
      }
      } catch (GridSearchTEParamsSelectionStrategy.RandomSelector.GridSearchCompleted ex) {
        // proceed... as we materialised whole hp space
      }
    return wholeSpace;
  }

  static HashMap<String, Object> singleRowFrameToMap(Frame bestHPsRow) {
    assert bestHPsRow.numRows() == 1;
    HashMap<String, Object> _grid = new HashMap<>();
    for(String hpName : bestHPsRow.names()) {
      Vec vec = bestHPsRow.vec(hpName);
      if(vec.isNumeric()) { // TODO we can probably change our HP space grid to have only numerics.
        _grid.put(hpName, vec.at(0));
      } else {
        throw new IllegalStateException("Unexpected type in hyperparameter search.");
      }
      
    }
    return _grid;
  };
  
  static Frame hyperspaceMapToFrame(GridSearchTEParamsSelectionStrategy.GridEntry[] wholeSpace) {
    HashMap<String, Object[]> hashMap = new HashMap<>();

    for(GridSearchTEParamsSelectionStrategy.GridEntry entry : wholeSpace) {
      Map<String, Object> entryMap = entry.getItem();
      for (Map.Entry<String, Object> item : entryMap.entrySet()) {
        Object[] toAppend = {item.getValue()};
        hashMap.put(item.getKey(), hashMap.containsKey(item.getKey()) ? TargetEncoderFrameHelper.concat(hashMap.get(item.getKey()), toAppend)  : toAppend);
      }
    }

    Frame spaceFrame = new Frame(Key.<Frame>make());
    for (Map.Entry<String, Object[]> item : hashMap.entrySet()) {
      Object[] values = item.getValue();
      Vec currentVec = null;
      if(values[0] instanceof Double ) {
        double[] dItems = new double[values.length]; 
        int i = 0;
        for(Object dValue : values){
          dItems[i] = (Double)dValue;
          i++;
        }
        currentVec = Vec.makeVec(dItems , Vec.newKey());
      }
      spaceFrame.add(item.getKey(), currentVec);
    }
    DKV.put(spaceFrame);
    return spaceFrame;
  }

  public String getResponseColumn() {
    return _responseColumn;
  }

  public String[] getColumnsToEncode() {
    return _columnsToEncode;
  }

  public boolean isTheBiggerTheBetter() {
    return _theBiggerTheBetter;
  } 

}
