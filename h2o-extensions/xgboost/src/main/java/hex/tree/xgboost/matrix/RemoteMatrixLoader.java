package hex.tree.xgboost.matrix;

import hex.tree.xgboost.exec.XGBoostExecReq;
import hex.tree.xgboost.exec.XGBoostHttpClient;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.apache.log4j.Logger;
import water.H2O;

import java.io.File;
import java.io.IOException;

public class RemoteMatrixLoader extends MatrixLoader {

    private static final Logger LOG = Logger.getLogger(RemoteMatrixLoader.class);

    private final String remoteDirectory;
    private final String remoteNodes[];

    public RemoteMatrixLoader(String remoteDirectory, String[] nodes) {
        this.remoteDirectory = remoteDirectory;
        this.remoteNodes = nodes;
    }

    @Override
    public DMatrix makeLocalMatrix() throws IOException, XGBoostError {
        final String remoteNode = remoteNodes[H2O.SELF.index()];
        assert remoteNode != null : "Should not be loading DMatrix on this node.";
        final File tempFile = File.createTempFile("dmatrix", ".bin");
        try {
        final XGBoostExecReq.GetMatrix req = new XGBoostExecReq.GetMatrix();
        req.matrix_dir_path = remoteDirectory;
        LOG.debug("Downloading matrix data into " + tempFile);
        final XGBoostHttpClient http = new XGBoostHttpClient(remoteNode);
        http.postFile(null, "getMatrix", req, tempFile);
        LOG.info("Downloading of remote matrix finished. Loading into memory.");
        
            return new DMatrix(tempFile.getAbsolutePath());
        } finally {
            tempFile.delete();
        }
    }

}
