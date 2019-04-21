package water.api.schemas3;

import hex.AUC2;
import hex.ConfusionMatrix;
import hex.ModelMetricsBinomial;
import water.api.API;
import water.api.SchemaServer;
import water.util.TwoDimTable;

public class ModelMetricsBinomialV3<I extends ModelMetricsBinomial, S extends ModelMetricsBinomialV3<I, S>>
    extends ModelMetricsBaseV3<I,S> {
//  @API(help="The standard deviation of the training response.", direction=API.Direction.OUTPUT)
//  public double sigma; // Belongs in a mythical ModelMetricsSupervisedV3

  @API(help="The R^2 for this scoring run.", direction=API.Direction.OUTPUT)
  public double r2;

  @API(help="The logarithmic loss for this scoring run.", direction=API.Direction.OUTPUT)
  public double logloss;

  @API(help="The AUC for this scoring run.", direction=API.Direction.OUTPUT)
  public double AUC;

  @API(help="The precision-recall AUC for this scoring run.", direction=API.Direction.OUTPUT)
  public double pr_auc;

  @API(help="The Gini score for this scoring run.", direction=API.Direction.OUTPUT)
  public double Gini;

  @API(help="The mean misclassification error per class.", direction=API.Direction.OUTPUT)
  public double mean_per_class_error;

  @API(help="The class labels of the response.", direction=API.Direction.OUTPUT)
  public String[] domain;

  @API(help = "The ConfusionMatrix at the threshold for maximum F1.", direction = API.Direction.OUTPUT)
  public ConfusionMatrixV3 cm;

  @API(help = "The Metrics for various thresholds.", direction = API.Direction.OUTPUT, level = API.Level.expert)
  public TwoDimTableV3 thresholds_and_metric_scores;

  @API(help = "The Metrics for various criteria.", direction = API.Direction.OUTPUT, level = API.Level.secondary)
  public TwoDimTableV3 max_criteria_and_metric_scores;

  @API(help = "Gains and Lift table.", direction = API.Direction.OUTPUT, level = API.Level.secondary)
  public TwoDimTableV3 gains_lift_table;

  @Override
  public S fillFromImpl(ModelMetricsBinomial modelMetrics) {
    super.fillFromImpl(modelMetrics);
//    sigma = modelMetrics._sigma;
    r2 = modelMetrics.r2();
    logloss = modelMetrics._logloss;
    mean_per_class_error = modelMetrics._mean_per_class_error;


    if (null != modelMetrics.cm()) {
      ConfusionMatrix cm = modelMetrics.cm();
      cm.table(); // Fill in lazy table, for icing
      this.cm = (ConfusionMatrixV3) SchemaServer.schema(this.getSchemaVersion(), cm).fillFromImpl(cm);
    }

    AUC2 auc = modelMetrics._auc;
    if (null != auc) {
      AUC  = auc._auc;
      Gini = auc._gini;
      pr_auc = auc._pr_auc;

      // Fill TwoDimTable
      String[] thresholds = new String[auc._nBins];
      for( int i=0; i<auc._nBins; i++ )
        thresholds[i] = Double.toString(auc._ths[i]);
      AUC2.ThresholdCriterion crits[] = AUC2.ThresholdCriterion.VALUES;
      String[] colHeaders = new String[crits.length+2];
      String[] colHeadersMax = new String[crits.length-8/*drop npr tpr etc.*/];
      String[] types      = new String[crits.length+2];
      String[] formats    = new String[crits.length+2];
      colHeaders[0] = "Threshold";
      types[0] = "double";
      formats[0] = "%f";
      int i;
      for( i=0; i<crits.length; i++ ) {
        if (colHeadersMax.length > i) colHeadersMax[i] = "max " + crits[i].toString();
        colHeaders[i+1] = crits[i].toString();
        types     [i+1] = crits[i]._isInt ? "long" : "double";
        formats   [i+1] = crits[i]._isInt ? "%d"   : "%f"    ;
      }
      colHeaders[i+1]  = "idx"; types[i+1] = "int"; formats[i+1] = "%d";
      TwoDimTable thresholdsByMetrics = new TwoDimTable("Metrics for Thresholds", "Binomial metrics as a function of classification thresholds", new String[auc._nBins], colHeaders, types, formats, null );
      for( i=0; i<auc._nBins; i++ ) {
        int j=0;
        thresholdsByMetrics.set(i, j, Double.valueOf(thresholds[i]));
        for (j = 0; j < crits.length; j++) {
          double d = crits[j].exec(auc, i); // Note: casts to Object are NOT redundant
          thresholdsByMetrics.set(i, 1+j, crits[j]._isInt ? (Object) ((long) d) : d);
        }
        thresholdsByMetrics.set(i, 1+j, i);
      }
      this.thresholds_and_metric_scores = new TwoDimTableV3().fillFromImpl(thresholdsByMetrics);

      // Fill TwoDimTable
      TwoDimTable maxMetrics = new TwoDimTable("Maximum Metrics", "Maximum metrics at their respective thresholds", colHeadersMax,
              new String[]{"Threshold","Value","idx"},
              new String[]{"double",   "double","long"},
              new String[]{"%f",       "%f",    "%d"},
              "Metric" );
      for( i=0; i<colHeadersMax.length; i++ ) {
        int idx = crits[i].max_criterion_idx(auc);
        maxMetrics.set(i,0,idx==-1 ? Double.NaN : auc._ths[idx]);
        maxMetrics.set(i,1,idx==-1 ? Double.NaN : crits[i].exec(auc,idx));
        maxMetrics.set(i,2,idx);
      }

      max_criteria_and_metric_scores = new TwoDimTableV3().fillFromImpl(maxMetrics);
    }
    if (modelMetrics._gainsLift != null) {
      TwoDimTable t = modelMetrics._gainsLift.createTwoDimTable();
      if (t!=null) this.gains_lift_table = new TwoDimTableV3().fillFromImpl(t);
    }
    return (S) this;
  }
}
