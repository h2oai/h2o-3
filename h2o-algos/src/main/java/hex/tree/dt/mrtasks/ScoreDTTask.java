package hex.tree.dt.mrtasks;


import hex.ModelMetrics;
import hex.tree.dt.DTModel;
import water.MRTask;
import water.fvec.Chunk;

public class ScoreDTTask extends MRTask<ScoreDTTask> {
    private DTModel _model;

    private ModelMetrics.MetricBuilder _metricsBuilder;

    public ScoreDTTask(DTModel _model) {
        this._model = _model;
    }

    @Override
    public void map(Chunk[] cs) {
        _metricsBuilder = _model.makeMetricBuilder(_model._output._domains[_model._output.responseIdx()]);
        double [] preds = new double[3];
        double [] tmp = new double[_model._output.nfeatures()];
        for (int row = 0; row < cs[0]._len; row++) {
            preds = _model.score0(cs, 0, row, tmp, preds);
            _metricsBuilder.perRow(preds, new float[]{(float) cs[0].atd(cs.length - 1)}, _model);
        }
    }

    @Override
    public void reduce(ScoreDTTask other) {
        _metricsBuilder.reduce(other._metricsBuilder);
    }

    public ModelMetrics.MetricBuilder getMetricsBuilder() {
        return _metricsBuilder;
    }
}
