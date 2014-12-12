package water.api;

import hex.AUCData;

import static hex.AUC.ThresholdCriterion;

public class AUCBase<I extends AUCData, S extends AUCBase<I, S>> extends Schema<I, S> {
  @API(help = "Thresholds (optional, e.g. 0:1:0.01 or 0.0,0.2,0.4,0.6,0.8,1.0).", direction=API.Direction.OUTPUT)
  public float[] thresholds;

  @API(help = "Threshold criterion", values={"maximum_F1", "maximum_F2", "maximum_F0point5", "maximum_Accuracy", "maximum_Precision", "maximum_Recall", "maximum_Specificity", "maximum_absolute_MCC", "minimizing_max_per_class_Error"}, direction=API.Direction.OUTPUT)
  public ThresholdCriterion threshold_criterion = ThresholdCriterion.maximum_F1;

  @API(help="domain of the actual response", direction=API.Direction.OUTPUT)
  public String[] actual_domain;

  @API(help="AUC (ROC)", direction=API.Direction.OUTPUT)
  public double AUC;

  @API(help="Gini", direction=API.Direction.OUTPUT)
  public double Gini;


  @API(help = "Confusion Matrices for all thresholds", direction=API.Direction.OUTPUT)
  public long[][][] confusion_matrices;

  @API(help = "F1 for all thresholds", direction=API.Direction.OUTPUT)
  public float[] F1;

  @API(help = "F2 for all thresholds", direction=API.Direction.OUTPUT)
  public float[] F2;

  @API(help = "F0point5 for all thresholds", direction=API.Direction.OUTPUT)
  public float[] F0point5;

  @API(help = "Accuracy for all thresholds", direction=API.Direction.OUTPUT)
  public float[] accuracy;

  @API(help = "Error for all thresholds", direction=API.Direction.OUTPUT)
  public float[] errorr;

  @API(help = "Precision for all thresholds", direction=API.Direction.OUTPUT)
  public float[] precision;

  @API(help = "Recall for all thresholds", direction=API.Direction.OUTPUT)
  public float[] recall;

  @API(help = "Specificity for all thresholds", direction=API.Direction.OUTPUT)
  public float[] specificity;

  @API(help = "MCC for all thresholds", direction=API.Direction.OUTPUT)
  public float[] mcc;

  @API(help = "Max per class error for all thresholds", direction=API.Direction.OUTPUT)
  public float[] max_per_class_error;


  @API(help="Threshold criteria", direction=API.Direction.OUTPUT)
  public String[] threshold_criteria;

  @API(help="Optimal thresholds for criteria", direction=API.Direction.OUTPUT)
  public float[] threshold_for_criteria;

  @API(help="F1 for threshold criteria", direction=API.Direction.OUTPUT)
  public float[] F1_for_criteria;

  @API(help="F2 for threshold criteria", direction=API.Direction.OUTPUT)
  public float[] F2_for_criteria;

  @API(help="F0point5 for threshold criteria", direction=API.Direction.OUTPUT)
  public float[] F0point5_for_criteria;

  @API(help="Accuracy for threshold criteria", direction=API.Direction.OUTPUT)
  public float[] accuracy_for_criteria;

  @API(help="Error for threshold criteria", direction=API.Direction.OUTPUT)
  public float[] error_for_criteria;

  @API(help="Precision for threshold criteria", direction=API.Direction.OUTPUT)
  public float[] precision_for_criteria;

  @API(help="Recall for threshold criteria", direction=API.Direction.OUTPUT)
  public float[] recall_for_criteria;

  @API(help="Specificity for threshold criteria", direction=API.Direction.OUTPUT)
  public float[] specificity_for_criteria;

  @API(help="MCC for threshold criteria", direction=API.Direction.OUTPUT)
  public float[] mcc_for_criteria;

  @API(help="Maximum per class Error for threshold criteria", direction=API.Direction.OUTPUT)
  public float[] max_per_class_error_for_criteria;

  @API(help="Confusion Matrices for threshold criteria", direction=API.Direction.OUTPUT)
  public long[][][] confusion_matrix_for_criteria;

}
