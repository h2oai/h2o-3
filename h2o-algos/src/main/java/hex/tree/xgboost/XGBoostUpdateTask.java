package hex.tree.xgboost;

import ml.dmlc.xgboost4j.java.*;
import water.MRTask;
import water.util.IcedHashMapGeneric;
import water.util.Log;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class XGBoostUpdateTask extends MRTask<XGBoostUpdateTask> {

    private final String[] _featureMap;
    private final String taskName;
    private final XGBoostModelInfo _sharedmodel;
    private final XGBoostOutput _output;
    private Booster booster;

    private final XGBoostModel.XGBoostParameters _parms;
    private final int tid;

    private IcedHashMapGeneric.IcedHashMapStringString rabitEnv = new IcedHashMapGeneric.IcedHashMapStringString();

    XGBoostUpdateTask(String taskName,
                      Booster booster,
                      XGBoostModelInfo inputModel,
                      String[] featureMap,
                      XGBoostOutput _output,
                      XGBoostModel.XGBoostParameters _parms,
                      int tid, Map<String, String> workerEnvs) {
        this.taskName = taskName;
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


        String boosterModelPath = "/tmp/booster" + taskName + "/";
        String boosterModel = boosterModelPath + taskName;

        if(booster == null) {
            // Done in local Rabit mode b/c createParams calls train() which isn't supposed to be distributed
            Map<String, String> localRabitEnv = new HashMap<>();
            localRabitEnv.put("DMLC_TASK_ID", Thread.currentThread().getName());
            Rabit.init(localRabitEnv);
            HashMap<String, Object> params = XGBoostModel.createParams(_parms, _output);
            Rabit.shutdown();

            Rabit.init(rabitEnv);
            HashMap<String, DMatrix> watches = new HashMap<>();
            booster = ml.dmlc.xgboost4j.java.XGBoost.train(trainMat, params, 0, watches, null, null);
        } else {
            Rabit.init(rabitEnv);
            booster.loadModel(boosterModel);
            booster.update(trainMat, tid);
        }

        File modelFile = new File(boosterModelPath);
        if(!modelFile.exists()) {
            modelFile.mkdirs();
            modelFile.deleteOnExit();
        }

        booster.saveModel(boosterModel);

        Rabit.shutdown();
    }

    Booster booster() {
        return booster;
    }

}