package hex.tree.isoforextended;

import hex.tree.isofor.ModelMetricsAnomaly;
import water.MRTask;
import water.fvec.Chunk;

class ScoreExtendedIsolationForestTask extends MRTask<ScoreExtendedIsolationForestTask> {
    private ExtendedIsolationForestModel _model;

    // output
    private ModelMetricsAnomaly.MetricBuilderAnomaly _metricsBuilder;

    public ScoreExtendedIsolationForestTask(ExtendedIsolationForestModel _model) {
        this._model = _model;
    }

    @Override
    public void map(Chunk[] cs) {
        _metricsBuilder = (ModelMetricsAnomaly.MetricBuilderAnomaly) _model.makeMetricBuilder(null);
        double [] preds = new double[2];
        double [] tmp = new double[cs.length];
        for (int row = 0; row < cs[0]._len; row++) {
            preds = _model.score0(cs, 0, row, tmp, preds);
            _metricsBuilder.perRow(preds, null, _model);
        }
    }

    @Override
    public void reduce(ScoreExtendedIsolationForestTask other) {
        _metricsBuilder.reduce(other._metricsBuilder);
    }

    public ModelMetricsAnomaly.MetricBuilderAnomaly getMetricsBuilder() {
        return _metricsBuilder;
    }
}
