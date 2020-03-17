package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostUtils;
import water.fvec.Frame;

import java.util.Map;

public class LocalXGBoostSetupTask extends XGBoostSetupTask {

    private final XGBoostModelInfo _sharedModel;
    private final boolean _sparse;
    private final Frame _trainFrame;

    public LocalXGBoostSetupTask(
        XGBoostModel model,
        XGBoostModel.XGBoostParameters parms,
        BoosterParms boosterParms,
        byte[] checkpointToResume,
        Map<String, String> rabitEnv,
        FrameNodes trainFrame
    ) {
        super(model, parms, boosterParms, checkpointToResume, rabitEnv, trainFrame);
        _sharedModel = model.model_info();
        _sparse = model._output._sparse;
        _trainFrame = trainFrame._fr;
    }


    @Override
    protected DMatrix makeLocalMatrix() throws XGBoostError {
        return XGBoostUtils.convertFrameToDMatrix(
            _sharedModel.dataInfo(),
            _trainFrame,
            _parms._response_column,
            _parms._weights_column,
            _parms._offset_column,
            _sparse
        );
    }
}
