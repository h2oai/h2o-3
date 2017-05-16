package hex.tree.xgboost;

import ml.dmlc.xgboost4j.java.*;
import water.H2O;
import water.MRTask;
import water.util.IcedHashMapGeneric;

import java.io.*;
import java.util.*;

public class XGBoostUpdateTask extends MRTask<XGBoostUpdateTask> {

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
                      XGBoostOutput _output,
                      XGBoostModel.XGBoostParameters _parms,
                      int tid, Map<String, String> workerEnvs) {
        this.taskName = taskName;
        this._sharedmodel = inputModel;
        this._output = _output;
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
        rabitEnv.put("DMLC_TASK_ID", String.valueOf(H2O.SELF.index()));
        String[] featureMap = new String[]{""};

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


        String boosterModelPath = "/tmp/booster" + taskName + H2O.SELF.index() + "/";
        String boosterModel = boosterModelPath + taskName;

        if(booster == null) {
            HashMap<String, Object> params = XGBoostModel.createParams(_parms, _output);

            Rabit.init(rabitEnv);
            HashMap<String, DMatrix> watches = new HashMap<>();
            booster = ml.dmlc.xgboost4j.java.XGBoost.train(trainMat, params, 0, watches, null, null);
        } else {
            Rabit.init(rabitEnv);
            booster = Booster.loadModel(boosterModel);
            BoosterUtils.loadRabitCheckpoint(booster);
            booster.update(trainMat, tid);
            BoosterUtils.saveRabitCheckpoint(booster);
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