package hex.tree.xgboost;

import hex.tree.xgboost.exec.XGBoostExecReq;
import hex.tree.xgboost.matrix.SparseMatrixDimensions;
import hex.tree.xgboost.remote.RemoteXGBoostHandler;
import hex.tree.xgboost.task.XGBoostUploadMatrixTask;
import hex.tree.xgboost.util.FeatureScore;
import water.TypeMapExtension;

public class XGBoostTypeMapExtension implements TypeMapExtension {

    private static final String[] EXTERNAL_COMMUNICATION_CLASSES = {
            XGBoostExecReq.class.getName(),
            XGBoostExecReq.Init.class.getName(),
            XGBoostExecReq.Update.class.getName(),
            SparseMatrixDimensions.class.getName(),
            RemoteXGBoostHandler.RemoteExecutors.class.getName(),
            XGBoostUploadMatrixTask.DenseMatrixChunk.class.getName(),
            XGBoostUploadMatrixTask.DenseMatrixDimensions.class.getName(),
            XGBoostUploadMatrixTask.MatrixData.class.getName(),
            XGBoostUploadMatrixTask.SparseMatrixChunk.class.getName(),
            FeatureScore.class.getName()
    };

    @Override
    public String[] getBoostrapClasses() {
        return EXTERNAL_COMMUNICATION_CLASSES;
    }

}
