package water.api;

import static java.util.Arrays.sort;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.HashSet;

public class AUC extends Iced {
//  static final int API_WEAVER = 1; // This file has auto-gen'd doc & json fields
//  static private DocGen.FieldDoc[] DOC_FIELDS; // Initialized from Auto-Gen code.
//  private static final String DOC_GET = "AUC";
//
//  @API(help = "", required = true, filter = Default.class, json=true)
  public Frame actual;

//  @API(help="Column of the actual results (will display vertically)", required=true, filter=actualVecSelect.class, json=true)
  public Vec vactual;
//  class actualVecSelect extends VecClassSelect { actualVecSelect() { super("actual"); } }

//  @API(help = "", required = true, filter = Default.class, json=true)
  public Frame predict;

//  @API(help="Column of the predicted results (will display horizontally)", required=true, filter=predictVecSelect.class, json=true)
  public Vec vpredict;
//  class predictVecSelect extends VecClassSelect { predictVecSelect() { super("predict"); } }

//  @API(help = "Thresholds (optional, e.g. 0:1:0.01 or 0.0,0.2,0.4,0.6,0.8,1.0).", required = false, filter = Default.class, json = true)
  private float[] thresholds;

//  @API(help = "Threshold criterion", filter = Default.class, json = true)
  public ThresholdCriterion threshold_criterion = ThresholdCriterion.maximum_F1;

  public enum ThresholdCriterion {
    maximum_F1,
    maximum_F2,
    maximum_F0point5,
    maximum_Accuracy,
    maximum_Precision,
    maximum_Recall,
    maximum_Specificity,
    maximum_absolute_MCC,
    minimizing_max_per_class_Error
  }

  @API(help = "AUC Data", json = true)
  AUCData aucdata;
  public AUCData data() { return aucdata; }

  public AUC() {}

  /**
   * Constructor for algos that make their own CMs
   * @param cms ConfusionMatrices
   * @param thresh Thresholds
   */
  public AUC(ConfusionMatrix2[] cms, float[] thresh) {
    this(cms, thresh, null);
  }
  /**
   * Constructor for algos that make their own CMs
   * @param cms ConfusionMatrices
   * @param thresh Thresholds
   * @param domain Domain
   */
  public AUC(ConfusionMatrix2[] cms, float[] thresh, String[] domain) {
    aucdata = new AUCData().compute(cms, thresh, domain, threshold_criterion);
  }

  private void init() throws IllegalArgumentException {
    // Input handling
    if( vactual==null || vpredict==null )
      throw new IllegalArgumentException("Missing vactual or vpredict!");
    if (vactual.length() != vpredict.length())
      throw new IllegalArgumentException("Both arguments must have the same length ("+vactual.length()+"!="+vpredict.length()+")!");
    if (!vactual.isInt())
      throw new IllegalArgumentException("Actual column must be integer class labels!");
    if (vactual.cardinality() != -1 && vactual.cardinality() != 2)
      throw new IllegalArgumentException("Actual column must contain binary class labels, but found cardinality " + vactual.cardinality() + "!");
    if (vpredict.isEnum())
      throw new IllegalArgumentException("vpredict cannot be class labels, expect probabilities.");
  }

  public void execImpl() {
    init();
    Vec va = null, vp;
    try {
      va = vactual.toEnum(); // always returns TransfVec
      vp = vpredict;
      // The vectors are from different groups => align them, but properly delete it after computation
      if (!va.group().equals(vp.group())) {
        vp = va.align(vp);
      }
      // compute thresholds, if not user-given
      if (thresholds != null) {
        sort(thresholds);
        if (ArrayUtils.minValue(thresholds) < 0) throw new IllegalArgumentException("Minimum threshold cannot be negative.");
        if (ArrayUtils.maxValue(thresholds) > 1) throw new IllegalArgumentException("Maximum threshold cannot be greater than 1.");
      } else {
        HashSet hs = new HashSet();
        final int bins = (int)Math.min(vpredict.length(), 200l);
        final long stride = Math.max(vpredict.length() / bins, 1);
        for( int i=0; i<bins; ++i) hs.add(new Float(vpredict.at(i*stride))); //data-driven thresholds TODO: use percentiles (from Summary2?)
        for (int i=0;i<51;++i) hs.add(new Float(i/50.)); //always add 0.02-spaced thresholds from 0 to 1

        // created sorted vector of unique thresholds
        thresholds = new float[hs.size()];
        int i=0;
        for (Object h : hs) {thresholds[i++] = (Float)h; }
        sort(thresholds);
      }
      // compute CMs
      aucdata = new AUCData().compute(new AUCTask(thresholds,va.mean()).doAll(va,vp).getCMs(), thresholds, va.factors(), threshold_criterion);
    } finally {       // Delete adaptation vectors
      if (va!=null) DKV.remove(va._key);
    }
  }

  /* return true if a is better than b with respect to criterion criter */
  static boolean isBetter(ConfusionMatrix2 a, ConfusionMatrix2 b, ThresholdCriterion criter) {
    if (criter == ThresholdCriterion.maximum_F1) {
      return (!Double.isNaN(a.F1()) &&
              (Double.isNaN(b.F1()) || a.F1() > b.F1()));
    } if (criter == ThresholdCriterion.maximum_F2) {
      return (!Double.isNaN(a.F2()) &&
              (Double.isNaN(b.F2()) || a.F2() > b.F2()));
    } if (criter == ThresholdCriterion.maximum_F0point5) {
      return (!Double.isNaN(a.F0point5()) &&
              (Double.isNaN(b.F0point5()) || a.F0point5() > b.F0point5()));
    } else if (criter == ThresholdCriterion.maximum_Recall) {
      return (!Double.isNaN(a.recall()) &&
              (Double.isNaN(b.recall()) || a.recall() > b.recall()));
    } else if (criter == ThresholdCriterion.maximum_Precision) {
      return (!Double.isNaN(a.precision()) &&
              (Double.isNaN(b.precision()) || a.precision() > b.precision()));
    } else if (criter == ThresholdCriterion.maximum_Accuracy) {
      return a.accuracy() > b.accuracy();
    } else if (criter == ThresholdCriterion.minimizing_max_per_class_Error) {
      return a.max_per_class_error() < b.max_per_class_error();
    } else if (criter == ThresholdCriterion.maximum_Specificity) {
      return (!Double.isNaN(a.specificity()) &&
              (Double.isNaN(b.specificity()) || a.specificity() > b.specificity()));
    } else if (criter == ThresholdCriterion.maximum_absolute_MCC) {
      return (!Double.isNaN(a.mcc()) &&
              (Double.isNaN(b.mcc()) || Math.abs(a.mcc()) > Math.abs(b.mcc())));
    }
    else {
      throw new IllegalArgumentException("Unknown threshold criterion.");
    }
  }

  public boolean toHTML( StringBuilder sb ) { return aucdata.toHTML(sb); }
  public void toASCII( StringBuilder sb ) { aucdata.toASCII(sb); }

  // Compute CMs for different thresholds via MRTask2
  private static class AUCTask extends MRTask<AUCTask> {
    /* @OUT CMs */ private final ConfusionMatrix2[] getCMs() { return _cms; }
    private ConfusionMatrix2[] _cms;
    double nullDev;
    double resDev;
    final double ymu;

    /* IN thresholds */ final private float[] _thresh;

    AUCTask(float[] thresh, double mu) {
      _thresh = thresh.clone();
      ymu = mu;
    }

    static final double y_log_y(double y, double mu) {
      if(y == 0)return 0;
      if(mu < Double.MIN_NORMAL) mu = Double.MIN_NORMAL;
      return y * Math.log(y / mu);
    }

    public static double binomial_deviance(double yreal, double ymodel){
      return 2 * ((y_log_y(yreal, ymodel)) + y_log_y(1 - yreal, 1 - ymodel));
    }
    @Override public void map( Chunk ca, Chunk cp ) {
      _cms = new ConfusionMatrix2[_thresh.length];
      for (int i=0;i<_cms.length;++i)
        _cms[i] = new ConfusionMatrix2(2);
      final int len = Math.min(ca.len(), cp.len());
      for( int i=0; i < len; i++ ) {
        if (ca.isNA0(i))
          throw new UnsupportedOperationException("Actual class label cannot be a missing value!");
        final int a = (int)ca.at80(i); //would be a 0 if double was NaN
        assert (a == 0 || a == 1) : "Invalid values in vactual: must be binary (0 or 1).";
        if (cp.isNA0(i)) {
//          Log.warn("Skipping predicted NaN."); //some models predict NaN!
          continue;
        }
        final double pr = cp.at0(i);
        for( int t=0; t < _cms.length; t++ ) {
          final int p = pr >= _thresh[t]?1:0;
          _cms[t].add(a, p);
        }
      }
    }

    @Override public void reduce( AUCTask other ) {
      for( int i=0; i<_cms.length; ++i) {
        _cms[i].add(other._cms[i]);
      }
      nullDev += other.nullDev;
      resDev  += other.resDev;
    }

    @Override public void postGlobal(){
    }
  }
}
