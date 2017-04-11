package hex.tree.xgboost;

import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.Rabit;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.IcedHashMapGeneric;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class XGBoostTask extends MRTask<XGBoostTask> {

    private Booster booster = null;
    private XGBoostModelInfo _sharedmodel;
    private XGBoostModelInfo _localmodel; //per-node state (to be reduced)

    private String[] _featureMap;
    private final XGBoostModel.XGBoostParameters _parms;

    private XGBoostOutput _output;

    private IcedHashMapGeneric.IcedHashMapStringString rabitEnv = new IcedHashMapGeneric.IcedHashMapStringString();

    XGBoostTask(XGBoostModelInfo inputModel,
                String[] featureMap,
                XGBoostOutput _output,
                Map<String, String> rabitEnvOrig,
                XGBoostModel.XGBoostParameters _parms) {
        super();
        this._sharedmodel = inputModel;
        this._output = _output;
        this._featureMap = featureMap;
        this._parms = _parms;
        rabitEnv.putAll(rabitEnvOrig);
    }

    @Override
    protected void setupLocal() {
        // Is this ok? What about scoring histories?
        _localmodel = _sharedmodel;

        try {
            // Get the training data for this node
            Frame _train = _parms.train();
            Chunk[] trainChunks = new Chunk[_train.vecs().length];
            for(int i = 0; i < _train.vecs().length; i++) {
                trainChunks[i] = _train.vec(i).chunkForChunkIdx(this._lo);
            }
            Chunk trainResponse = _train.vec(_parms._response_column).chunkForChunkIdx(this._lo);
            Chunk trainWeights = _parms._weights_column == null ? null : _train.vec(_parms._weights_column).chunkForChunkIdx(this._lo);

            DMatrix trainMat = XGBoost.convertFrametoDMatrix(_localmodel._dataInfoKey,
                    _parms.train(),
                    trainChunks,
                    trainResponse,
                    trainWeights,
                    _parms._fold_column,
                    _featureMap,
                    _output._sparse);

            DMatrix validMat = null;
            if(null != _parms._valid) {
                Frame _valid = _parms.valid();
                Chunk[] validChunks = new Chunk[_valid.vecs().length];
                for(int i = 0; i < _valid.vecs().length; i++) {
                    validChunks[i] = _valid.vec(i).chunkForChunkIdx(this._lo);
                }
                Chunk validResponse = _valid.vec(_parms._response_column).chunkForChunkIdx(this._lo);
                Chunk validWeights = _parms._weights_column == null ? null : _valid.vec(_parms._weights_column).chunkForChunkIdx(this._lo);

                validMat = XGBoost.convertFrametoDMatrix(_localmodel._dataInfoKey,
                        _parms.valid(),
                        validChunks,
                        validResponse,
                        validWeights,
                        _parms._fold_column,
                        _featureMap,
                        _output._sparse);
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
        } catch (XGBoostError xgBoostError) {
            xgBoostError.printStackTrace();
        }
    }

    // Do we need a reducer here or can we just put this in setupLocal?
    @Override
    public void reduce(XGBoostTask mrt) {
        super.reduce(mrt);
        if(null != mrt.booster) {
            this.booster = mrt.booster;
        }
    }

    Booster booster() {
        return booster;
    }
}
