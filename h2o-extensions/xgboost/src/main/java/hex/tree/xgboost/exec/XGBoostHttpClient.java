package hex.tree.xgboost.exec;

import hex.genmodel.utils.IOUtils;
import hex.schemas.XGBoostExecReqV3;
import hex.schemas.XGBoostExecRespV3;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import water.Key;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static water.util.HttpResponseStatus.OK;

public class XGBoostHttpClient {

    private static final Logger LOG = Logger.getLogger(XGBoostHttpClient.class);

    private final String baseUri;

    interface ResponseTransformer<T> {
        T transform(HttpEntity e) throws IOException;
    }

    private static final ResponseTransformer<byte[]> ByteArrayResponseTransformer = (e) -> {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copyStream(e.getContent(), bos);
        bos.close();
        byte[] b = bos.toByteArray();
        if (b.length == 0) return null;
        else return b;
    };

    private static final ResponseTransformer<XGBoostExecRespV3> JsonResponseTransformer = (e) -> {
        String responseBody = EntityUtils.toString(e);
        XGBoostExecRespV3 resp = new XGBoostExecRespV3();
        resp.fillFromBody(responseBody);
        return resp;
    };

    public XGBoostHttpClient(String baseUri) {
        this.baseUri = "http://" + baseUri + "/3/XGBoostExecutor.";
    }

    public XGBoostExecRespV3 postJson(Key key, String method, XGBoostExecReq reqContent) {
        return post(key, method, reqContent, JsonResponseTransformer);
    }

    public byte[] postBytes(Key key, String method, XGBoostExecReq reqContent) {
        return post(key, method, reqContent, ByteArrayResponseTransformer);
    }

    public File postFile(Key key, String method, XGBoostExecReq reqContent, File dest) {
        return post(key, method, reqContent, (e) -> {
            IOUtils.copyStream(e.getContent(), new FileOutputStream(dest));
            return dest;
        });
    }

    private <T> T post(Key key, String method, XGBoostExecReq reqContent, ResponseTransformer<T> transformer) {
        LOG.info("Request " + method + " " + key + " " + reqContent);
        XGBoostExecReqV3 req = new XGBoostExecReqV3(key, reqContent);
        HttpPost httpReq = new HttpPost(baseUri + method);
        httpReq.setEntity(new StringEntity(req.toJsonString(), UTF_8));
        httpReq.setHeader(CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        try (CloseableHttpClient client = HttpClientBuilder.create().build();
             CloseableHttpResponse response = client.execute(httpReq)) {
            if (response.getStatusLine().getStatusCode() != OK.getCode()) {
                throw new IllegalStateException("Unexpected response (status: " + response.getStatusLine() + ").");
            }
            LOG.debug("Response received " + response.getEntity().getContentLength() + " bytes.");
            return transformer.transform(response.getEntity());
        } catch (IOException e) {
            throw new RuntimeException("HTTP Request failed", e);
        }
    }

}
