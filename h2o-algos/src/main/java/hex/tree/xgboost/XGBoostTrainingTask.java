package hex.tree.xgboost;

import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.Rabit;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.MRTask;
import water.util.IcedHashMapGeneric;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class XGBoostTrainingTask extends MRTask<XGBoostTrainingTask> {

    private Booster booster = null;
    private XGBoostModelInfo _sharedmodel;

    private String[] _featureMap;
    private final XGBoostModel.XGBoostParameters _parms;

    private XGBoostOutput _output;

    private IcedHashMapGeneric.IcedHashMapStringString rabitEnv = new IcedHashMapGeneric.IcedHashMapStringString();

    XGBoostTrainingTask(XGBoostModelInfo inputModel,
                        String[] featureMap,
                        XGBoostOutput _output,
                        Map<String, String> rabitEnvOrig,
                        XGBoostModel.XGBoostParameters _parms) {
        this._sharedmodel = inputModel;
        this._output = _output;
        this._featureMap = featureMap;
        this._parms = _parms;
        rabitEnv.putAll(rabitEnvOrig);
    }

    @Override
    protected void setupLocal() {
        try {
            DMatrix trainMat = XGBoost.convertFrametoDMatrix(_sharedmodel._dataInfoKey,
                    _parms.train(),
                    this._lo,
                    this._hi - 1,
                    _parms._response_column,
                    _parms._weights_column,
                    _parms._fold_column,
                    _featureMap,
                    _output._sparse);

            DMatrix validMat = null;
            if(null != _parms._valid) {
                // Find, if any, _valid frame chunks on this node
                int validLo = -1;
                int validHi = -1;
                for(int i = 0; i < _parms.valid().anyVec().nChunks(); i++) {
                    if(_parms.valid().anyVec().chunkKey(i).home()) {
                        if(validLo == -1) {
                            validLo = i;
                        }
                        validHi = i;
                    }
                }

                if(validLo != -1 && validHi != -1) {
                    validMat = XGBoost.convertFrametoDMatrix(_sharedmodel._dataInfoKey,
                            _parms.valid(),
                            validLo,
                            validHi,
                            _parms._response_column,
                            _parms._weights_column,
                            _parms._fold_column,
                            _featureMap,
                            _output._sparse);
                }
            }

            // For feature importances - write out column info
            OutputStream os;
            try {
                os = new FileOutputStream("/tmp/featureMap.txt");
                os.write(_featureMap[0].getBytes());
                os.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            HashMap<String, DMatrix> watches = new HashMap<>();
            if (validMat!=null)
                watches.put("valid", validMat);
            else
                watches.put("train", trainMat);

            rabitEnv.put("XGBOOST_TASK_ID", Thread.currentThread().getName());
            Rabit.init(rabitEnv);
            // create the backend
            booster = ml.dmlc.xgboost4j.java.XGBoost.train(trainMat, XGBoostModel.createParams(_parms, _output), 0, watches, null, null);

            Rabit.shutdown();

            trainMat.dispose();
            if(null != validMat) {
                validMat.dispose();
            }
        } catch (XGBoostError xgBoostError) {
            xgBoostError.printStackTrace();
        }
    }

    // Do we need a reducer here or can we just put this in setupLocal?
    @Override
    public void reduce(XGBoostTrainingTask mrt) {
        super.reduce(mrt);
        if(null != mrt.booster) {
            this.booster = mrt.booster;
        }
    }

    Booster booster() {
        return booster;
    }
}
