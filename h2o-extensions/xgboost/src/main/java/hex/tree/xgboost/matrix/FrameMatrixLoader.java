package hex.tree.xgboost.matrix;

import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.XGBoostUtils;
import water.fvec.Frame;

public class FrameMatrixLoader extends MatrixLoader {

    private final XGBoostModelInfo _modelInfo;
    private final XGBoostModel.XGBoostParameters _parms;
    private final boolean _sparse;
    private final Frame _trainFrame;
    private final Frame _validFrame;

    public FrameMatrixLoader(XGBoostModel model, Frame train, Frame validFrame) {
        _modelInfo = model.model_info();
        _parms = model._parms;
        _sparse = model._output._sparse;
        _trainFrame = train;
        _validFrame = validFrame;
    }

    @Override
    public DMatrixProvider makeLocalTrainMatrix() {
        return XGBoostUtils.convertFrameToDMatrix(
            _modelInfo.dataInfo(),
            _trainFrame,
            _parms._response_column,
            _parms._weights_column,
            _parms._offset_column,
            _sparse
        );
    }

    @Override
    public boolean hasValidationFrame() {
        return _validFrame != null;
    }

    @Override
    public DMatrixProvider makeLocalValidMatrix() {
        return XGBoostUtils.convertFrameToDMatrix(
                _modelInfo.dataInfo(),
                _validFrame,
                _parms._response_column,
                _parms._weights_column,
                _parms._offset_column,
                _sparse
        );
    }

}
