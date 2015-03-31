package water.api;

import hex.AUC2;
import hex.ConfusionMatrix;
import hex.ModelMetricsBinomial;
import water.util.TwoDimTable;

public class ModelMetricsBinomialV3<I extends ModelMetricsBinomial, S extends ModelMetricsBinomialV3<I, S>> extends ModelMetricsBase<I,S> {
  @API(help="The standard deviation of the training response.", direction=API.Direction.OUTPUT)
    public double sigma; // Belongs in a mythical ModelMetricsSupervisedV3

  @API(help="The logarithmic loss for this scoring run.", direction=API.Direction.OUTPUT)
    public double logloss;

  @API(help="The AUC for this scoring run.", direction=API.Direction.OUTPUT)
    public double AUC;

  @API(help="The Gini score for this run.", direction=API.Direction.OUTPUT)
    public double Gini;

  @API(help = "The Metrics for various thresholds.", direction = API.Direction.OUTPUT)
    public TwoDimTableV1 thresholds_and_metric_scores;

  @API(help = "The Metrics for various criteria.", direction = API.Direction.OUTPUT)
    public TwoDimTableV1 max_criteria_and_metric_scores;

  @Override
    public ModelMetricsBinomialV3 fillFromImpl(ModelMetricsBinomial modelMetrics) {
    super.fillFromImpl(modelMetrics);
    this.sigma = modelMetrics._sigma;
    this.logloss = modelMetrics._logloss;

    AUC2 auc = modelMetrics._auc;
    if (null != auc) {
      this.AUC  = auc._auc;
      this.Gini = auc._gini;
      
      // Fill TwoDimTable
      String[] thresholds = new String[auc._nBins];
      for( int i=0; i<auc._nBins; i++ )
        thresholds[i] = Double.toString(auc._ths[i]);
      String[] colHeaders = new String[2+AUC2.ThresholdCriterion.VALUES.length];
      String[] types      = new String[2+AUC2.ThresholdCriterion.VALUES.length];
      String[] formats    = new String[2+AUC2.ThresholdCriterion.VALUES.length];
      colHeaders[0] = "True Positives";  types[0] = "long";  formats[0] = "%d";
      colHeaders[1] = "False Positives"; types[1] = "long";  formats[1] = "%d";
      for( int i=0; i<AUC2.ThresholdCriterion.VALUES.length; i++ ) {
        colHeaders[i+2] = AUC2.ThresholdCriterion.VALUES[i].toString();
        types     [i+2] = "double";
        formats   [i+2] = "%f";
      }
      TwoDimTable thresholdsByMetrics = new TwoDimTable("Thresholds x Metric Scores", null, thresholds, colHeaders, types, formats, "Thresholds" );
      for( int i=0; i<auc._nBins; i++ ) {
        thresholdsByMetrics.set(i,0,auc._tps[i]);
        thresholdsByMetrics.set(i,1,auc._fps[i]);
        for( int j=0; j<AUC2.ThresholdCriterion.VALUES.length; j++ )
          thresholdsByMetrics.set(i,j+2,AUC2.ThresholdCriterion.VALUES[j].exec(auc,i));
      }
      this.thresholds_and_metric_scores = new TwoDimTableV1().fillFromImpl(thresholdsByMetrics);
      
      // Fill TwoDimTable
      TwoDimTable maxMetrics = new TwoDimTable("Maximum Metric", null, colHeaders,
                                               new String[]{"Threshold","Metric","idx"},
                                               new String[]{"double",   "double","long"},
                                               new String[]{"%f",       "%f",    "%d"},
                                               "Metric" );
      maxMetrics.set(0,0,auc._ths[auc._nBins-1]);
      maxMetrics.set(0,1,auc._tps[auc._nBins-1]);
      maxMetrics.set(0,2,auc._nBins-1);
      
      maxMetrics.set(1,0,auc._ths[auc._nBins-1]);
      maxMetrics.set(1,1,auc._fps[auc._nBins-1]);
      maxMetrics.set(1,2,auc._nBins-1);

      for( int i=0; i<auc._nBins; i++ ) {
        int idx = AUC2.ThresholdCriterion.VALUES[i].max_criterion_idx(auc);
        maxMetrics.set(i+2,0,auc._ths[idx]);
        maxMetrics.set(i+2,1,AUC2.ThresholdCriterion.VALUES[i].exec(auc,idx));
        maxMetrics.set(i+2,2,idx);
      }
      
      this.max_criteria_and_metric_scores = new TwoDimTableV1().fillFromImpl(maxMetrics);
    }
    return this;
  }
}
