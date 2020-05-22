package hex.tree.xgboost.task;

import hex.tree.xgboost.exec.XGBoostHttpClient;
import hex.tree.xgboost.matrix.FrameMatrixLoader;
import org.apache.log4j.Logger;
import water.H2O;
import water.Key;

import java.io.*;

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
        try {
            LOG.debug("Writing matrix data into memory buffer");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(bos);
            os.writeObject(matrixLoader.makeLocalMatrix());
            os.close();
            LOG.debug("Written " + bos.size() + " bytes to memory, uploading.");
            client.uploadBytes(_modelKey, "matrix", bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save the matrix to file-system.", e);
        }
    }

}
