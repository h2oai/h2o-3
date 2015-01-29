package hex;

import java.util.HashSet;

import water.Iced;
import static hex.AUC.ThresholdCriterion;
import static hex.AUC.isBetter;

public class AUCData extends Iced {
  public float[] thresholds;
  public ThresholdCriterion threshold_criterion = ThresholdCriterion.maximum_F1;
  private String [] actual_domain;
  public double AUC;
  public double Gini;

  public long[][][] confusion_matrices;
  public float[] F1;
  public float[] F2;
  public float[] F0point5;
  public float[] accuracy;
  public float[] errorr;
  public float[] precision;
  public float[] recall;
  public float[] specificity;
  public float[] mcc;
  public float[] max_per_class_error;

  String[] threshold_criteria;
  private float[] threshold_for_criteria;
  private float[] F1_for_criteria;
  private float[] F2_for_criteria;
  private float[] F0point5_for_criteria;
  private float[] accuracy_for_criteria;
  private float[] error_for_criteria;
  private float[] precision_for_criteria;
  private float[] recall_for_criteria;
  private float[] specificity_for_criteria;
  private float[] mcc_for_criteria;
  private float[] max_per_class_error_for_criteria;
  private long[][][] confusion_matrix_for_criteria;

  /* Independent on thresholds */
  public double AUC() { return AUC; }
  public double Gini() { return Gini; }

  /* Return the metrics for given criterion */
  public double F1(ThresholdCriterion criter) { return _cms[idxCriter[criter.ordinal()]].F1(); }
  public double F2(ThresholdCriterion criter) { return _cms[idxCriter[criter.ordinal()]].F2(); }
  public double F0point5(ThresholdCriterion criter) { return _cms[idxCriter[criter.ordinal()]].F0point5(); }
  public double precision(ThresholdCriterion criter) { return _cms[idxCriter[criter.ordinal()]].precision(); }
  public double recall(ThresholdCriterion criter) { return _cms[idxCriter[criter.ordinal()]].recall(); }
  public double specificity(ThresholdCriterion criter) { return _cms[idxCriter[criter.ordinal()]].specificity(); }
  public double mcc(ThresholdCriterion criter) { return _cms[idxCriter[criter.ordinal()]].mcc(); }
  public double accuracy(ThresholdCriterion criter) { return _cms[idxCriter[criter.ordinal()]].accuracy(); }
  public double err(ThresholdCriterion criter) { return _cms[idxCriter[criter.ordinal()]].err(); }
  public double max_per_class_error(ThresholdCriterion criter) { return _cms[idxCriter[criter.ordinal()]].max_per_class_error(); }
  public float threshold(ThresholdCriterion criter) { return threshold_for_criteria[criter.ordinal()]; }
  public long[][] cm(ThresholdCriterion criter) { return confusion_matrix_for_criteria[criter.ordinal()]; }


  /* Return the metrics for chosen threshold criterion */
  public double F1() { return F1(threshold_criterion); }
  public double F2() { return F2(threshold_criterion); }
  public double F0point5() { return F0point5(threshold_criterion); }
  public double err() { return err(threshold_criterion); }
  public double precision() { return precision(threshold_criterion); }
  public double recall() { return recall(threshold_criterion); }
  public double specificity() { return specificity(threshold_criterion); }
  public double mcc() { return mcc(threshold_criterion); }
  public double accuracy() { return accuracy(threshold_criterion); }
  public double max_per_class_error() { return max_per_class_error(threshold_criterion); }
  public float threshold() { return threshold(threshold_criterion); }
  public long[][] cm() { return cm(threshold_criterion); }
  public ConfusionMatrix CM() { return _cms[idxCriter[threshold_criterion.ordinal()]]; }

  /* Return the best possible metrics */
  public double bestF1() { return F1(ThresholdCriterion.maximum_F1); }
  public double bestErr() { return err(ThresholdCriterion.maximum_Accuracy); }

  /* Helpers */
  private int[] idxCriter;
  private double[] _tprs;
  private double[] _fprs;
  private ConfusionMatrix[] _cms;

  private static double trapezoid_area(double x1, double x2, double y1, double y2) { return Math.abs(x1-x2)*(y1+y2)/2.; }

  public AUCData compute(ConfusionMatrix[] cms, float[] thresh, String[] domain, ThresholdCriterion criter) {
    _cms = cms;
    thresholds = thresh;
    threshold_criterion = criter;
    actual_domain = domain;
    assert(_cms.length == thresholds.length):("incompatible lengths of thresholds and confusion matrices: " + _cms.length + " != " + thresholds.length);
    // compute AUC and best thresholds
    computeAUC();
    findBestThresholds(thresh);
    computeMetrics();
    return this;
  }

  private void computeAUC() {
    _tprs = new double[_cms.length];
    _fprs = new double[_cms.length];
    double TPR_pre = 1;
    double FPR_pre = 1;
    AUC = 0;
    for( int t = 0; t < _cms.length; ++t ) {
      double TPR = 1 - _cms[t].classErr(1); // =TP/(TP+FN) = true-positive-rate
      double FPR = _cms[t].classErr(0); // =FP/(FP+TN) = false-positive-rate
      AUC += trapezoid_area(FPR_pre, FPR, TPR_pre, TPR);
      TPR_pre = TPR;
      FPR_pre = FPR;
      _tprs[t] = TPR;
      _fprs[t] = FPR;
    }
    AUC += trapezoid_area(FPR_pre, 0, TPR_pre, 0);
    assert(AUC > -1e-5 && AUC < 1.+1e-5); //check numerical sanity
    AUC = Math.max(0., Math.min(AUC, 1.)); //clamp to 0...1
    Gini = 2*AUC-1;
  }

  private void findBestThresholds(float[] thresholds) {
    threshold_criteria = new String[ThresholdCriterion.values().length];
    int i=0;
    HashSet<ThresholdCriterion> hs = new HashSet<ThresholdCriterion>();
    for (ThresholdCriterion criter : ThresholdCriterion.values()) {
      hs.add(criter);
      threshold_criteria[i++] = criter.toString().replace("_", " ");
    }
    confusion_matrix_for_criteria = new long[hs.size()][][];
    idxCriter = new int[hs.size()];
    threshold_for_criteria = new float[hs.size()];
    F1_for_criteria = new float[hs.size()];
    F2_for_criteria = new float[hs.size()];
    F0point5_for_criteria = new float[hs.size()];
    accuracy_for_criteria = new float[hs.size()];
    error_for_criteria = new float[hs.size()];
    precision_for_criteria = new float[hs.size()];
    recall_for_criteria = new float[hs.size()];
    specificity_for_criteria = new float[hs.size()];
    mcc_for_criteria = new float[hs.size()];
    max_per_class_error_for_criteria = new float[hs.size()];

    for (ThresholdCriterion criter : hs) {
      final int id = criter.ordinal();
      idxCriter[id] = 0;
      threshold_for_criteria[id] = thresholds[0];
      for(i = 1; i < _cms.length; ++i) {
        if (isBetter(_cms[i], _cms[idxCriter[id]], criter)) {
          idxCriter[id] = i;
          threshold_for_criteria[id] = thresholds[i];
        }
      }
      // Set members for JSON, float to save space
      confusion_matrix_for_criteria[id] = _cms[idxCriter[id]].confusion_matrix;
      F1_for_criteria[id] = (float)_cms[idxCriter[id]].F1();
      F2_for_criteria[id] = (float)_cms[idxCriter[id]].F2();
      F0point5_for_criteria[id] = (float)_cms[idxCriter[id]].F0point5();
      accuracy_for_criteria[id] = (float)_cms[idxCriter[id]].accuracy();
      error_for_criteria[id] = (float)_cms[idxCriter[id]].err();
      precision_for_criteria[id] = (float)_cms[idxCriter[id]].precision();
      recall_for_criteria[id] = (float)_cms[idxCriter[id]].recall();
      specificity_for_criteria[id] = (float)_cms[idxCriter[id]].specificity();
      mcc_for_criteria[id] = (float)_cms[idxCriter[id]].mcc();
      max_per_class_error_for_criteria[id] = (float)_cms[idxCriter[id]].max_per_class_error();
    }
  }

  /**
   * Populate requested JSON fields
   */
  private void computeMetrics() {
    confusion_matrices = new long[_cms.length][][];
    F1 = new float[_cms.length];
    F2 = new float[_cms.length];
    F0point5 = new float[_cms.length];
    accuracy = new float[_cms.length];
    errorr = new float[_cms.length];
    precision = new float[_cms.length];
    recall = new float[_cms.length];
    specificity = new float[_cms.length];
    mcc = new float[_cms.length];
    max_per_class_error = new float[_cms.length];
    for(int i=0;i<_cms.length;++i) {
      confusion_matrices[i] = _cms[i].confusion_matrix;
      F1[i] = (float)_cms[i].F1();
      F2[i] = (float)_cms[i].F2();
      F0point5[i] = (float)_cms[i].F0point5();
      accuracy[i] = (float)_cms[i].accuracy();
      errorr[i] = (float)_cms[i].err();
      precision[i] = (float)_cms[i].precision();
      recall[i] = (float)_cms[i].recall();
      specificity[i] = (float)_cms[i].specificity();
      mcc[i] = (float)_cms[i].mcc();
      max_per_class_error[i] = (float)_cms[i].max_per_class_error();
    }
  } // computeMetrics()
}
