package water.api.schemas3;

import hex.AUC2;
import hex.ModelMetricsMultinomial;

import hex.PairwiseAUC;
import water.api.API;
import water.api.SchemaServer;
import water.util.TwoDimTable;

import static hex.ModelMetricsMultinomial.*;

public class ModelMetricsMultinomialV3<I extends ModelMetricsMultinomial, S extends ModelMetricsMultinomialV3<I, S>>
    extends ModelMetricsBaseV3<I, S> {
  @API(help="The R^2 for this scoring run.", direction=API.Direction.OUTPUT)
  public double r2;

  @API(help="The hit ratio table for this scoring run.", direction=API.Direction.OUTPUT, level= API.Level.expert)
  public TwoDimTableV3 hit_ratio_table;

  @API(help="The ConfusionMatrix object for this scoring run.", direction=API.Direction.OUTPUT)
  public ConfusionMatrixV3 cm;

  @API(help="The logarithmic loss for this scoring run.", direction=API.Direction.OUTPUT)
  public double logloss;

  @API(help="The logarithmic likelihood for this scoring run.", direction=API.Direction.OUTPUT)
  public double loglikelihood;

  @API(help="The AIC for this scoring run.", direction=API.Direction.OUTPUT)
  public double AIC;

  @API(help="The mean misclassification error per class.", direction=API.Direction.OUTPUT)
  public double mean_per_class_error;

  @API(help="The average AUC for this scoring run.", direction=API.Direction.OUTPUT)
  public double AUC;

  @API(help="The average precision-recall AUC for this scoring run.", direction=API.Direction.OUTPUT)
  public double pr_auc;

  @API(help="The multinomial AUC values.", direction=API.Direction.OUTPUT, level= API.Level.expert)
  public TwoDimTableV3 multinomial_auc_table;

  @API(help="The multinomial PR AUC values.", direction=API.Direction.OUTPUT, level= API.Level.expert)
  public TwoDimTableV3 multinomial_aucpr_table;

  @Override
  public S fillFromImpl(I modelMetrics) {
    super.fillFromImpl(modelMetrics);
    logloss = modelMetrics.logloss();
    loglikelihood = modelMetrics.loglikelihood();
    AIC = modelMetrics.aic();
    
    r2 = modelMetrics.r2();

    if (modelMetrics._hit_ratios != null) {
      TwoDimTable table = getHitRatioTable(modelMetrics._hit_ratios);
      hit_ratio_table = (TwoDimTableV3) SchemaServer.schema(this.getSchemaVersion(), table).fillFromImpl(table);
    }
    
    AUC = modelMetrics.auc();
    pr_auc = modelMetrics.pr_auc();
    
    if (modelMetrics._auc != null && modelMetrics._auc._calculateAuc) {
      TwoDimTable aucTable = modelMetrics._auc.getTable(false);
      multinomial_auc_table = (TwoDimTableV3) SchemaServer.schema(this.getSchemaVersion(), aucTable).fillFromImpl(aucTable);

      TwoDimTable aucprTable = modelMetrics._auc.getTable(true);
      multinomial_aucpr_table = (TwoDimTableV3) SchemaServer.schema(this.getSchemaVersion(), aucprTable).fillFromImpl(aucprTable);
    }

    if (null != modelMetrics._cm) {
      modelMetrics._cm.table();  // Fill in lazy table, for icing
      cm = (ConfusionMatrixV3) SchemaServer.schema(this.getSchemaVersion(), modelMetrics._cm).fillFromImpl
          (modelMetrics._cm);
    }

    return (S)this;
  }
}
