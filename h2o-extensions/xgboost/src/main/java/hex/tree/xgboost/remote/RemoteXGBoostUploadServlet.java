package hex.tree.xgboost.remote;

import hex.genmodel.utils.IOUtils;
import hex.schemas.XGBoostExecRespV3;
import hex.tree.xgboost.matrix.RemoteMatrixLoader;
import hex.tree.xgboost.matrix.SparseMatrixDimensions;
import hex.tree.xgboost.task.XGBoostUploadMatrixTask;
import org.apache.log4j.Logger;
import water.*;
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
        matrixTrain,
        matrixValid
    }

    public enum MatrixRequestType {
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
            String modelKey = request.getParameter("model_key");
            String requestType = request.getParameter("request_type");
            LOG.info("Upload request for " + modelKey + " " + requestType + " received");
            
            RequestType type = RequestType.valueOf(requestType);
            if (type == RequestType.checkpoint) {
                File destFile = getCheckpointFile(modelKey);
                saveIntoFile(destFile, request);
            } else if (type == RequestType.matrixTrain || type == RequestType.matrixValid) {
                Key<?> key = Key.make(modelKey);
                MatrixRequestType matrixRequestType = MatrixRequestType.valueOf(request.getParameter("data_type"));
                String matrixKey = type == RequestType.matrixTrain ? 
                        RemoteMatrixLoader.trainMatrixKey(key) : RemoteMatrixLoader.validMatrixKey(key);
                handleMatrixRequest(matrixKey, matrixRequestType, request);
            }
            response.setContentType("application/json");
            response.getWriter().write(new XGBoostExecRespV3(Key.make(modelKey)).toJsonString());
        } catch (Exception e) {
            ServletUtils.sendErrorResponse(response, e, uri);
        } finally {
            ServletUtils.logRequest("POST", request, response);
        }
    }

    private void handleMatrixRequest(String matrixKey, MatrixRequestType type, HttpServletRequest request) throws IOException {
        BootstrapFreezable<?> requestData;
        try (AutoBuffer ab = new AutoBuffer(request.getInputStream(), TypeMap.bootstrapClasses())) {
            requestData = ab.get();
        }
        switch (type) {
            case sparseMatrixDimensions:
                RemoteMatrixLoader.initSparse(matrixKey, (SparseMatrixDimensions) requestData);
                break;
            case sparseMatrixChunk:
                RemoteMatrixLoader.sparseChunk(matrixKey, (XGBoostUploadMatrixTask.SparseMatrixChunk) requestData);
                break;
            case denseMatrixDimensions:
                RemoteMatrixLoader.initDense(matrixKey, (XGBoostUploadMatrixTask.DenseMatrixDimensions) requestData);
                break;
            case denseMatrixChunk:
                RemoteMatrixLoader.denseChunk(matrixKey, (XGBoostUploadMatrixTask.DenseMatrixChunk) requestData);
                break;
            case matrixData:
                RemoteMatrixLoader.matrixData(matrixKey, (XGBoostUploadMatrixTask.MatrixData) requestData);
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
