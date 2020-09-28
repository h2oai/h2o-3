package hex.tree.xgboost.remote;

import hex.genmodel.utils.IOUtils;
import hex.schemas.XGBoostExecRespV3;
import org.apache.log4j.Logger;
import water.H2O;
import water.Key;
import water.server.ServletUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class RemoteXGBoostUploadServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(RemoteXGBoostUploadServlet.class);
    
    public static File getUploadDir(String key) {
        return new File(H2O.ICE_ROOT.toString(), key);
    }
    
    public static File getMatrixFile(String key) {
        return new File(getUploadDir(key), "matrix.part" + H2O.SELF.index());
    }
    
    public static File getCheckpointFile(String key) {
        return new File(getUploadDir(key), "checkpoint.bin");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        String uri = ServletUtils.getDecodedUri(request);
        try {
            String model_key = request.getParameter("model_key");
            String data_type = request.getParameter("data_type");
            LOG.info("Upload request for " + model_key + " " + data_type + " received");
            File destFile;
            File uploadDir = getUploadDir(model_key);
            if (uploadDir.mkdirs()) {
                LOG.debug("Created temporary directory " + uploadDir);
            }
            if ("matrix".equalsIgnoreCase(data_type)) {
                destFile = getMatrixFile(model_key);
            } else {
                destFile = getCheckpointFile(model_key);
            }
            LOG.debug("Saving contents into " + destFile);
            InputStream is = request.getInputStream();
            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                IOUtils.copyStream(is, fos);
            }
            response.setContentType("application/json");
            response.getWriter().write(new XGBoostExecRespV3(Key.make(model_key)).toJsonString());
        } catch (Exception e) {
            ServletUtils.sendErrorResponse(response, e, uri);
        } finally {
            ServletUtils.logRequest("POST", request, response);
        }
    }

}
