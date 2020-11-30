package hex.tree.xgboost.remote;

import hex.genmodel.utils.IOUtils;
import hex.schemas.XGBoostExecRespV3;
import hex.tree.xgboost.matrix.RemoteMatrixLoader;
import hex.tree.xgboost.matrix.SparseMatrixDimensions;
import hex.tree.xgboost.task.XGBoostUploadMatrixTask;
import org.apache.log4j.Logger;
import water.H2O;
import water.Key;
import water.server.ServletUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

public class RemoteXGBoostUploadServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(RemoteXGBoostUploadServlet.class);
    
    public static File getUploadDir(String key) {
        return new File(H2O.ICE_ROOT.toString(), key);
    }
    
    public static File getCheckpointFile(String key) {
        File uploadDir = getUploadDir(key);
        if (uploadDir.mkdirs()) {
            LOG.debug("Created temporary directory " + uploadDir);
        }
        return new File(getUploadDir(key), "checkpoint.bin");
    }
    
    public enum RequestType {
        checkpoint,
        sparseMatrixDimensions,
        sparseMatrixChunk,
        denseMatrixDimensions,
        denseMatrixChunk,
        matrixData
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        String uri = ServletUtils.getDecodedUri(request);
        try {
            String model_key = request.getParameter("model_key");
            String data_type = request.getParameter("data_type");
            LOG.info("Upload request for " + model_key + " " + data_type + " received");
            RequestType type = RequestType.valueOf(data_type);
            if (type == RequestType.checkpoint) {
                File destFile = getCheckpointFile(model_key);
                saveIntoFile(destFile, request);
            } else {
                handleMatrixRequest(model_key, type, request);
            }
            response.setContentType("application/json");
            response.getWriter().write(new XGBoostExecRespV3(Key.make(model_key)).toJsonString());
        } catch (Exception e) {
            ServletUtils.sendErrorResponse(response, e, uri);
        } finally {
            ServletUtils.logRequest("POST", request, response);
        }
    }

    private void handleMatrixRequest(String model_key, RequestType type, HttpServletRequest request) throws IOException, ClassNotFoundException {
        Object requestData = new ObjectInputStream(request.getInputStream()).readObject();
        switch (type) {
            case sparseMatrixDimensions:
                RemoteMatrixLoader.initSparse(model_key, (SparseMatrixDimensions) requestData);
                break;
            case sparseMatrixChunk:
                RemoteMatrixLoader.sparseChunk(model_key, (XGBoostUploadMatrixTask.SparseMatrixChunk) requestData);
                break;
            case denseMatrixDimensions:
                RemoteMatrixLoader.initDense(model_key, (XGBoostUploadMatrixTask.DenseMatrixDimensions) requestData);
                break;
            case denseMatrixChunk:
                RemoteMatrixLoader.denseChunk(model_key, (XGBoostUploadMatrixTask.DenseMatrixChunk) requestData);
                break;
            case matrixData:
                RemoteMatrixLoader.matrixData(model_key, (XGBoostUploadMatrixTask.MatrixData) requestData);
                break;
            default:
                throw new IllegalArgumentException("Unexpected request type: " + type);
        }
    }

    private void saveIntoFile(File destFile, HttpServletRequest request) throws IOException {
        LOG.debug("Saving contents into " + destFile);
        InputStream is = request.getInputStream();
        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            IOUtils.copyStream(is, fos);
        }
    }

}
