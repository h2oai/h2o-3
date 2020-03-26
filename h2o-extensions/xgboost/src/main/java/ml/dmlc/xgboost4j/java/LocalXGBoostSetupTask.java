package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostUtils;
import water.fvec.Frame;

import java.util.Map;

public class LocalXGBoostSetupTask extends XGBoostSetupTask {

    private final XGBoostModelInfo _modelInfo;
    private final XGBoostModel.XGBoostParameters _parms;
    private final boolean _sparse;
    private final Frame _trainFrame;

    public LocalXGBoostSetupTask(
        XGBoostModel model,
        XGBoostModel.XGBoostParameters parms,
        BoosterParms boosterParms,
        byte[] checkpointToResume,
        Map<String, String> rabitEnv,
        FrameNodes train
    ) {
        super(model._key, parms._save_matrix_directory, boosterParms, checkpointToResume, rabitEnv, train._nodes);
        _modelInfo = model.model_info();
        _parms = parms;
        _sparse = model._output._sparse;
        _trainFrame = train._fr;
    }

    @Override
    protected DMatrix makeLocalMatrix() throws XGBoostError {
        return XGBoostUtils.convertFrameToDMatrix(
            _modelInfo.dataInfo(),
            _trainFrame,
            _parms._response_column,
            _parms._weights_column,
            _parms._offset_column,
            _sparse
        );
    }
}
