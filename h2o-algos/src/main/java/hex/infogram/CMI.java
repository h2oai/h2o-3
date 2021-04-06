package hex.infogram;

/**
 * This class is written to calculate the conditional mutual information of various predictors with and without a 
 * sensitive attribute set.
 */
public class CMI {
  public static void calFairInfoGram() {
    // build the base model with response, sensitive attribute
    // score with base model
    // calculate mean(log2())
    // calculate a model for each predictor plus sensitive attribute
    // score with each model
    // calculate mean(log2())
    ;
  }
  
  public static void calCoreInfoGram() {
    // build the base model with response, all predictors
    // score with base model
    // calculate mean(log2())
    // calculate a model taken out each predictor column at a time
    // score with each model
    // calculate mean(log2())
    ;
  }
}
