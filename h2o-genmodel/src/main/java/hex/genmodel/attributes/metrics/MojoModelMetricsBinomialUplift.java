package hex.genmodel.attributes.metrics;

import hex.genmodel.attributes.SerializedName;
import hex.genmodel.attributes.Table;

public class MojoModelMetricsBinomialUplift extends MojoModelMetricsSupervised {
    
    @SerializedName("AUUC")
    public double _auuc;
    public double _normalized_auuc;
    @SerializedName("Qini")
    public double _qini;
    public double _ate;
    public double _att;
    public double _atc;
    public Table _thresholds_and_metric_scores;
    public Table _auuc_table;
    public Table _aecu_table;
}
