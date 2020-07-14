package hex.tree.xgboost.task;

import hex.tree.xgboost.exec.XGBoostHttpClient;
import hex.tree.xgboost.matrix.FrameMatrixLoader;
import hex.tree.xgboost.matrix.MatrixLoader;
import org.apache.log4j.Logger;
import water.H2O;
import water.Key;

public class XGBoostUploadMatrixTask extends AbstractXGBoostTask<XGBoostUploadMatrixTask> {

    private static final Logger LOG = Logger.getLogger(XGBoostUploadMatrixTask.class);

    private final FrameMatrixLoader matrixLoader;
    private final String[] remoteNodes;
    private final boolean https;
    private final String userName;
    private final String password;

    public XGBoostUploadMatrixTask(
        Key modelKey, boolean[] frameNodes, FrameMatrixLoader loader, String[] remoteNodes, boolean https, String userName, String password
    ) {
        super(modelKey, frameNodes);
        this.matrixLoader = loader;
        this.remoteNodes = remoteNodes;
        this.https = https;
        this.userName = userName;
        this.password = password;
    }

    @Override
    protected void execute() {
        XGBoostHttpClient client = new XGBoostHttpClient(remoteNodes[H2O.SELF.index()], https, userName, password);
        LOG.info("Preparing matrix part to upload.");
        MatrixLoader.DMatrixProvider data = matrixLoader.makeLocalMatrix();
        client.uploadObject(_modelKey, "matrix", data);
    }

}
