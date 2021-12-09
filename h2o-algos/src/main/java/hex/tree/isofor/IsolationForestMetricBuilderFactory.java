package hex.tree.isofor;


import com.google.gson.JsonObject;
import hex.ModelMetrics;
import hex.genmodel.IMetricBuilder;
import hex.genmodel.algos.isofor.IsolationForestMojoModel;

public class IsolationForestMetricBuilderFactory extends ModelMetrics.MetricBuilderFactory<IsolationForestModel, IsolationForestMojoModel>{

    @Override
    public IMetricBuilder createBuilder(IsolationForestMojoModel mojoModel, JsonObject extraInfo) {
        return new ModelMetricsAnomaly.IndependentMetricBuilderAnomaly("Anomaly metrics", false);
    }
}
