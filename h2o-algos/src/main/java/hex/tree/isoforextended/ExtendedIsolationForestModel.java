package hex.tree.isoforextended;

import hex.ModelMetrics;
import hex.tree.SharedTreeModel;
import hex.tree.isofor.ModelMetricsAnomaly;
import water.Key;
import water.util.SBPrintStream;

/**
 * 
 * @author Adam Valenta
 */
public class ExtendedIsolationForestModel extends SharedTreeModel<ExtendedIsolationForestModel,
                                                                    ExtendedIsolationForestParameters,
                                                                    ExtendedIsolationForestOutput> {
    
    public ExtendedIsolationForestModel(Key<ExtendedIsolationForestModel> selfKey, ExtendedIsolationForestParameters parms,
                                        ExtendedIsolationForestOutput output) {
        super(selfKey, parms, output);
//        iTrees = new ExtendedIsolationForest.ITree[_parms._ntrees];
    }

    @Override
    protected void toJavaUnifyPreds(SBPrintStream body) {
        // avalenta - TODO
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        return new ModelMetricsAnomaly.MetricBuilderAnomaly("Isolation Forest Metrics");
    }
}
