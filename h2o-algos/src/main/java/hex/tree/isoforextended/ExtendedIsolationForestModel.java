package hex.tree.isoforextended;

import hex.ModelMetrics;
import hex.tree.SharedTreeModel;
import hex.tree.isofor.ModelMetricsAnomaly;
import water.Iced;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.MathUtils;
import water.util.SBPrintStream;

/**
 * 
 * @author Adam Valenta
 */
public class ExtendedIsolationForestModel extends SharedTreeModel<ExtendedIsolationForestModel,
                                                                    ExtendedIsolationForestParameters,
                                                                    ExtendedIsolationForestOutput> {
    public IsolationTree[] iTrees;
    
    public ExtendedIsolationForestModel(Key<ExtendedIsolationForestModel> selfKey, ExtendedIsolationForestParameters parms,
                                        ExtendedIsolationForestOutput output) {
        super(selfKey, parms, output);
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        return new ModelMetricsAnomaly.MetricBuilderAnomaly("Extended Isolation Forest Metrics");
    }

    @Override
    protected String[] makeScoringNames(){
        return new String[]{"anomaly_score", "mean_length"};
    }

    @Override
    protected String[][] makeScoringDomains(Frame adaptFrm, boolean computeMetrics, String[] names) {
        assert names.length == 2;
        return new String[2][];
    }

    @Override 
    protected double[] score0(double[] data, double[] preds, double offset, int ntrees) {
        super.score0(data, preds, offset, ntrees);
        if (ntrees >= 1) preds[1] = preds[0] / ntrees;
        Vec row = Vec.makeVec(data, Vec.newKey());
        
        // compute score for given point
        double pathLength = 0;
        for (IsolationTree iTree : iTrees) {
            double iTreeScore = iTree.computePathLength(row);
            pathLength += iTreeScore;
//            System.out.println("iTreeScore " + iTreeScore);
        }
        pathLength = pathLength / iTrees.length;
//        System.out.println("pathLength " + pathLength);
        double anomalyScore = anomalyScore(pathLength);
//        System.out.println("Anomaly score " + anomalyScore);
        preds[0] = anomalyScore;
        preds[1] = pathLength;
        return preds;
    }

    @Override protected void toJavaUnifyPreds(SBPrintStream body) {
        throw new UnsupportedOperationException("Extended Isolation Forest support only MOJOs.");
    }

    private double anomalyScore(double pathLength) {
        return Math.pow(2, -1 * (pathLength / IsolationTree.averagePathLengthOfUnsuccesfullSearch(_parms.sampleSize)));
    }
}
