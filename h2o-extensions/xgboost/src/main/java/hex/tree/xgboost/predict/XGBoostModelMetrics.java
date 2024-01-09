package hex.tree.xgboost.predict;

import hex.Model;
import hex.ModelMetrics;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import hex.tree.xgboost.task.XGBoostScoreTask;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.CFuncRef;

import java.util.Arrays;

import static water.fvec.Vec.T_CAT;
import static water.fvec.Vec.T_NUM;

public class XGBoostModelMetrics {

    private final XGBoostOutput _output;
    private final Frame _data;
    private final Frame _originalData;
    private final XGBoostModel _model;
    
    private final XGBoostScoreTask _task;

    public XGBoostModelMetrics(
        XGBoostOutput output,
        Frame data,
        Frame originalData,
        boolean isTrain,
        XGBoostModel model,
        CFuncRef customMetricFunc
    ) {
        _output = output;
        _data = data;
        _originalData = originalData;
        _model = model;

        _task = new XGBoostScoreTask(
            _output, _data.find(_model._parms._weights_column), isTrain, _model, customMetricFunc
        );
    }

    public ModelMetrics compute() {
        Scope.enter();
        try {
            Frame preds = Scope.track(runScoreTask());
            if (_output.nclasses() == 1) {
                Vec pred = preds.vec(0);
                return _task._metricBuilder.makeModelMetrics(_model, _originalData, _data, new Frame(pred));
            } else if (_output.nclasses() == 2) {
                Vec p1 = preds.vec(2);
                return _task._metricBuilder.makeModelMetrics(_model, _originalData, _data, new Frame(p1));
            } else {
                Frame pp = new Frame(preds);
                pp.remove(0);
                return _task._metricBuilder.makeModelMetrics(_model, _originalData, _data, pp);
            }
        } finally {
            Scope.exit();
        }
    }

    private Frame runScoreTask() {
        _task.doAll(outputTypes(), _data);
        final String[] names = Model.makeScoringNames(_output);
        return _task.outputFrame(null, names, makeDomains(names));
    }

    private byte[] outputTypes() {
        // Last output is the response, which eventually will be removed before returning the preds Frame but is needed to build metrics
        if (_output.nclasses() == 1) {
            return new byte[]{T_NUM};
        } else if (_output.nclasses() == 2) {
            return new byte[]{T_CAT, T_NUM, T_NUM};
        } else {
            byte[] types = new byte[_output.nclasses() + 1];
            Arrays.fill(types, T_NUM);
            return types;
        }
    }

    private String[][] makeDomains(String[] names) {
        if (_output.nclasses() == 1) {
            return null;
        } else {
            String[][] domains = new String[names.length][];
            domains[0] = _output.classNames();
            return domains;
        }
    }

}
