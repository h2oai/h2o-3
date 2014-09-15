package water.api;

import water.AUCData;
import water.util.BeanUtils;

import static water.AUC.ThresholdCriterion;

public class AUCBase extends Schema<AUCData, AUCBase> {
  @API(help = "Thresholds (optional, e.g. 0:1:0.01 or 0.0,0.2,0.4,0.6,0.8,1.0).")
  public float[] thresholds;

  @API(help = "Threshold criterion")
  public ThresholdCriterion threshold_criterion = ThresholdCriterion.maximum_F1;

  @API(help="domain of the actual response")
  public String [] actual_domain;

  @API(help="AUC (ROC)")
  public double AUC;

  @API(help="Gini")
  public double Gini;


  @API(help = "Confusion Matrices for all thresholds")
  public long[][][] confusion_matrices;

  @API(help = "F1 for all thresholds")
  public float[] F1;

  @API(help = "F2 for all thresholds")
  public float[] F2;

  @API(help = "F0point5 for all thresholds")
  public float[] F0point5;

  @API(help = "Accuracy for all thresholds")
  public float[] accuracy;

  @API(help = "Error for all thresholds")
  public float[] errorr;

  @API(help = "Precision for all thresholds")
  public float[] precision;

  @API(help = "Recall for all thresholds")
  public float[] recall;

  @API(help = "Specificity for all thresholds")
  public float[] specificity;

  @API(help = "MCC for all thresholds")
  public float[] mcc;

  @API(help = "Max per class error for all thresholds")
  public float[] max_per_class_error;


  @API(help="Threshold criteria")
  public String[] threshold_criteria;

  @API(help="Optimal thresholds for criteria")
  public float[] threshold_for_criteria;

  @API(help="F1 for threshold criteria")
  public float[] F1_for_criteria;

  @API(help="F2 for threshold criteria")
  public float[] F2_for_criteria;

  @API(help="F0point5 for threshold criteria")
  public float[] F0point5_for_criteria;

  @API(help="Accuracy for threshold criteria")
  public float[] accuracy_for_criteria;

  @API(help="Error for threshold criteria")
  public float[] error_for_criteria;

  @API(help="Precision for threshold criteria")
  public float[] precision_for_criteria;

  @API(help="Recall for threshold criteria")
  public float[] recall_for_criteria;

  @API(help="Specificity for threshold criteria")
  public float[] specificity_for_criteria;

  @API(help="MCC for threshold criteria")
  public float[] mcc_for_criteria;

  @API(help="Maximum per class Error for threshold criteria")
  public float[] max_per_class_error_for_criteria;

  @API(help="Confusion Matrices for threshold criteria")
  public long[][][] confusion_matrix_for_criteria;

  // Version&Schema-specific filling into the implementation object
  public AUCData createImpl() {
    AUCData auc_data = new AUCData();
    BeanUtils.copyProperties(auc_data, this, BeanUtils.FieldNaming.CONSISTENT);
    return auc_data;
  }

  // Version&Schema-specific filling from the implementation object
  public AUCBase fillFromImpl(AUCData i) {
    BeanUtils.copyProperties(this, i, BeanUtils.FieldNaming.CONSISTENT);
    return this;
  }
}
