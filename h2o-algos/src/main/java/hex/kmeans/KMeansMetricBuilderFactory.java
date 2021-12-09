package hex.kmeans;

import com.google.gson.JsonObject;
import hex.ModelMetrics;
import hex.ModelMetricsClustering;
import hex.genmodel.IMetricBuilder;
import hex.genmodel.algos.kmeans.KMeansMojoModel;

public class KMeansMetricBuilderFactory extends ModelMetrics.MetricBuilderFactory<KMeansModel, KMeansMojoModel> {
    @Override
    public IMetricBuilder createBuilder(KMeansMojoModel mojoModel, JsonObject extraInfo) {
        return new ModelMetricsClustering.IndependentMetricBuilderClustering(
            mojoModel.nfeatures(),
            mojoModel.getNumClusters(),
            mojoModel._centers,
            mojoModel._modes
        );
    }
}
