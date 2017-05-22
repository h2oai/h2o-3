package hex.tree.xgboost;

import ml.dmlc.xgboost4j.java.*;
import water.H2O;
import water.MRTask;
import water.util.IcedHashMapGeneric;

import java.io.*;
import java.util.*;

public class XGBoostUpdateTask extends MRTask<XGBoostUpdateTask> {

    private final XGBoostModelInfo _sharedmodel;
    private final XGBoostOutput _output;
    private transient Booster booster;
    private byte[] rawBooster;

    private final XGBoostModel.XGBoostParameters _parms;
    private final int tid;

    private IcedHashMapGeneric.IcedHashMapStringString rabitEnv = new IcedHashMapGeneric.IcedHashMapStringString();

    XGBoostUpdateTask(Booster booster,
                      XGBoostModelInfo inputModel,
                      XGBoostOutput _output,
                      XGBoostModel.XGBoostParameters _parms,
                      int tid, Map<String, String> workerEnvs) {
        this._sharedmodel = inputModel;
        this._output = _output;
        this._parms = _parms;
        this.tid = tid;
        this.rawBooster = XGBoost.getRawArray(booster);
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
        HashMap<String, Object> params = XGBoostModel.createParams(_parms, _output);

        rabitEnv.put("DMLC_TASK_ID", String.valueOf(H2O.SELF.index()));
        String[] featureMap = new String[]{""};

        // DON'T put this before createParams, createPrams calls train() which isn't supposed to be distributed
        // just to check if we have GPU on the machine
        Rabit.init(rabitEnv);

        DMatrix trainMat = XGBoost.convertFrametoDMatrix(
                _sharedmodel._dataInfoKey,
                _fr,
                true,
                _parms._response_column,
                _parms._weights_column,
                _parms._fold_column,
                featureMap,
                _output._sparse);

        // For feature importances - write out column info
        OutputStream os;
        try {
          os = new FileOutputStream("/tmp/featureMap.txt");
          os.write(featureMap[0].getBytes());
          os.close();
        } catch (FileNotFoundException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }

        if(rawBooster == null) {
            HashMap<String, DMatrix> watches = new HashMap<>();
            booster = ml.dmlc.xgboost4j.java.XGBoost.train(trainMat, params, 0, watches, null, null);
        } else {
            try {
                booster = Booster.loadModel(new ByteArrayInputStream(rawBooster));
                // Set the parameters, some seem to get lost on save/load
                booster.setParams(params);
            } catch (IOException e) {
                e.printStackTrace();
            }

            booster.update(trainMat, tid);
        }

        Rabit.shutdown();
    }

    Booster booster() {
        return booster;
    }

}