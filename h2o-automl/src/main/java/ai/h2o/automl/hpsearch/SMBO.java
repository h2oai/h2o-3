package ai.h2o.automl.hpsearch;

import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import org.apache.commons.math3.stat.inference.TestUtils;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.util.TwoDimTable;

import java.util.Random;

/**
 * Sequential model-based optimisation(SMBO) method is a more general name for Bayesian optimisation method for hyperparameters search. 
 * Bayesian optimisation methods build probability models of the objective functions but in general we can use other approaches/models 
 * that can predict score of the objective function with less time required and with utilisation of the history of scores. 
 */
public abstract class SMBO {

  private Frame _history;
  /**
   * Contains mappings of hps to scores under objective function
   * @return
   */
  public Frame history() {
    return _history;
  };

  public boolean hasNoPrior() {
    return _history == null || _history.numRows() == 0;
  };

  public void updatePrior(Frame hyperparametersWithScores){
    _history = TargetEncoderFrameHelper.rBind(_history, hyperparametersWithScores);
  };

  /**
   *  Should be overwritten by user with given objective function evaluation
   * @return
   */
  //  public abstract double objective(Frame hyperparameters); 
  
  public abstract SurrogateModel surrogateModel();
  
  public abstract SMBOSelectionCreteria selectionCriteria();
  
  public Frame getNextBestHyperparameters(Frame unexploredHyperspace) {
    
    // 1) evaluate whole searchspace with surrogate model. Model is trained on history.
    Frame evaluatedHyperspace = surrogateModel().evaluate(unexploredHyperspace, history());
    int predictionIdx = evaluatedHyperspace.find("prediction");
    Frame sorted = evaluatedHyperspace.sort(new int[] {predictionIdx}, new int[] {-1});
    printOutFrameAsTable(sorted, false, 30);
    // 2) choose best HyperParameters based on `selectionCriteria`
    Frame bestHPsBasedOnCriteria = null;
    boolean randomStrategy = false;
    if(randomStrategy) {
      int nextRandomIndx = new Random().nextInt((int)sorted.numRows());
      bestHPsBasedOnCriteria = sorted.deepSlice(new long[]{nextRandomIndx}, null);
    }
    else {
      bestHPsBasedOnCriteria = sorted.deepSlice(new long[]{0}, null);
    }
    bestHPsBasedOnCriteria._key = Key.make("best_candidate_" + Key.make());
    DKV.put(bestHPsBasedOnCriteria);

    //Removing predictions as on the next iteration we will have updated prior and new predictions
    unexploredHyperspace.remove(predictionIdx).remove();
    
    printOutFrameAsTable(bestHPsBasedOnCriteria);
    return bestHPsBasedOnCriteria;
  };

  //TODO for dev. remove
  public static void printOutFrameAsTable(Frame fr) {
    printOutFrameAsTable(fr, false, fr.numRows());
  }

  public static void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
    assert limit <= Integer.MAX_VALUE;
    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) limit, rollups);
    System.out.println(twoDimTable.toString(2, true));
  }
}
