package hex.tree.xgboost.matrix;

import hex.tree.xgboost.exec.XGBoostExecReq;
import hex.tree.xgboost.exec.XGBoostHttpClient;
import org.apache.log4j.Logger;
import water.H2O;

import java.io.*;

public class RemoteMatrixLoader extends MatrixLoader {

    private static final Logger LOG = Logger.getLogger(RemoteMatrixLoader.class);

    private final String remoteDirectory;
    private final String[] remoteNodes;

    public RemoteMatrixLoader(String remoteDirectory, String[] nodes) {
        this.remoteDirectory = remoteDirectory;
        this.remoteNodes = nodes;
    }

    private DMatrixProvider loadProvider(File file) {
        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(file))) {
            return (DMatrixProvider) is.readObject();
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException("Failed to read matrix data into memory", e);
        }
    }
    
    @Override
    public DMatrixProvider makeLocalMatrix() throws IOException {
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
            return loadProvider(tempFile);
        } finally {
            if (!tempFile.delete()) {
                LOG.warn("Failed to delete temporary file at " + tempFile);
            }
        }
    }

}
