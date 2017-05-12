package hex.tree.xgboost;

import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.Rabit;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.MRTask;
import water.util.IcedHashMapGeneric;
import water.util.Log;

import java.util.Map;

public class XGBoostUpdateTask extends MRTask<XGBoostUpdateTask> {

    private final String[] _featureMap;
    private final XGBoostModelInfo _sharedmodel;
    private final XGBoostOutput _output;
    private Booster booster;

    private final XGBoostModel.XGBoostParameters _parms;
    private final int tid;

    private IcedHashMapGeneric.IcedHashMapStringString rabitEnv = new IcedHashMapGeneric.IcedHashMapStringString();

    XGBoostUpdateTask(Booster booster,
                      XGBoostModelInfo inputModel,
                      String[] featureMap,
                      XGBoostOutput _output,
                      XGBoostModel.XGBoostParameters _parms,
                      int tid, Map<String, String> workerEnvs) {
        this._sharedmodel = inputModel;
        this._output = _output;
        this._featureMap = featureMap;
        this._parms = _parms;
        this.tid = tid;
        this.booster = booster;
        rabitEnv.putAll(workerEnvs);
    }


    @Override
    protected void setupLocal() {
        try {
            update();
        } catch (XGBoostError xgBoostError) {
            xgBoostError.printStackTrace();
        }
    }

    private void update() throws XGBoostError {
        rabitEnv.put("DMLC_TASK_ID", Thread.currentThread().getName());

        Rabit.init(rabitEnv);

        DMatrix trainMat = XGBoost.convertFrametoDMatrix(
                _sharedmodel._dataInfoKey,
                _fr,
                this._lo,
                // TODO fix this, should be _hi?
                this._nhi - 1,
                _parms._response_column,
                _parms._weights_column,
                _parms._fold_column,
                _featureMap,
                _output._sparse);


        //booster.setParam("eta", effective_learning_rate(model));

        booster.update(trainMat, tid);

        Rabit.shutdown();
    }

    Booster booster() {
        return booster;
    }

}