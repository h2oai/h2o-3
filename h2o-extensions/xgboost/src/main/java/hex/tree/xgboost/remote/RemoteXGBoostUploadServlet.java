package hex.tree.xgboost.remote;

import hex.genmodel.utils.IOUtils;
import hex.schemas.XGBoostExecRespV3;
import hex.tree.xgboost.task.XGBoostSetupTask;
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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        String uri = ServletUtils.getDecodedUri(request);
        try {
            String model_key = request.getParameter("model_key");
            String data_type = request.getParameter("data_type");
            LOG.debug("Upload request for " + model_key + " " + data_type + " received");
            final String dataDirPath = H2O.ICE_ROOT.toString() + "/" + model_key;
            File destFile;
            if ("matrix".equalsIgnoreCase(data_type)) {
                destFile = XGBoostSetupTask.getMatrixFile(new File(dataDirPath));
            } else {
                destFile = new File(dataDirPath, "checkpoint.bin");
            }
            LOG.debug("Saving contents into " + destFile);
            InputStream is = ServletUtils.extractPartInputStream(request, response);
            if (is == null) {
                throw new IllegalArgumentException("Request missing file upload.");
            }
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
