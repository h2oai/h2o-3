package hex.tree.xgboost.exec;

import hex.genmodel.utils.IOUtils;
import hex.schemas.XGBoostExecReqV3;
import hex.schemas.XGBoostExecRespV3;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.AbstractContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import water.Key;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static water.util.HttpResponseStatus.OK;

public class XGBoostHttpClient {

    private static final Logger LOG = Logger.getLogger(XGBoostHttpClient.class);

    private final String baseUri;
    private final HttpClientBuilder clientBuilder;

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

    public XGBoostHttpClient(String baseUri, boolean https, String userName, String password) {
        this.baseUri = (https ? "https" : "http") + "://" + baseUri + "/3/XGBoostExecutor.";
        this.clientBuilder = createClientBuilder(https, userName, password);
    }

    private HttpClientBuilder createClientBuilder(boolean https, String userName, String password) {
        try {
            HttpClientBuilder builder = HttpClientBuilder.create();
            if (https) {
                SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(TrustSelfSignedStrategy.INSTANCE)
                    .build();
                SSLConnectionSocketFactory sslFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    NoopHostnameVerifier.INSTANCE
                );
                builder.setSSLSocketFactory(sslFactory);
            }
            if (userName != null) {
                CredentialsProvider provider = new BasicCredentialsProvider();
                UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(userName, password);
                provider.setCredentials(AuthScope.ANY, credentials);
                builder.setDefaultCredentialsProvider(provider);
            }
            return builder;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize HTTP client.", e);
        }
    }

    public XGBoostExecRespV3 postJson(Key key, String method, XGBoostExecReq reqContent) {
        return post(key, method, reqContent, JsonResponseTransformer);
    }

    public byte[] downloadBytes(Key key, String method, XGBoostExecReq reqContent) {
        return post(key, method, reqContent, ByteArrayResponseTransformer);
    }

    private <T> T post(Key key, String method, XGBoostExecReq reqContent, ResponseTransformer<T> transformer) {
        LOG.info("Request " + method + " " + key + " " + reqContent);
        XGBoostExecReqV3 req = new XGBoostExecReqV3(key, reqContent);
        HttpPost httpReq = new HttpPost(baseUri + method);
        httpReq.setEntity(new StringEntity(req.toJsonString(), UTF_8));
        httpReq.setHeader(CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        return executeRequestAndReturnResponse(httpReq, transformer);
    }
    
    private HttpPost makeUploadRequest(Key key, String dataType) {
        try {
            URIBuilder uri = new URIBuilder(baseUri + "upload");
            uri.setParameter("model_key", key.toString())
                .setParameter("data_type", dataType);
            return new HttpPost(uri.build());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to build request URI.", e);
        }
    }
    
    public void uploadBytes(Key key, String dataType, byte[] data) {
        LOG.info("Request upload " + key + " " + dataType + " " + data.length + " bytes");
        HttpPost httpReq = makeUploadRequest(key, dataType);
        HttpEntity entity = MultipartEntityBuilder.create()
            .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
            .addBinaryBody("file", data, ContentType.DEFAULT_BINARY, "upload")
            .build();
        httpReq.setEntity(entity);
        XGBoostExecRespV3 resp = executeRequestAndReturnResponse(httpReq, JsonResponseTransformer);
        assert resp.key.key().equals(key);
    }
    
    private static class ObjectBody extends AbstractContentBody {
        
        private final Object object;

        private ObjectBody(Object object) {
            super(ContentType.DEFAULT_BINARY);
            this.object = object;
        }

        @Override
        public String getFilename() {
            return "upload";
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            LOG.debug("Sending matrix data");
            ObjectOutputStream os = new ObjectOutputStream(out);
            os.writeObject(object);
            os.flush();
        }

        @Override
        public String getTransferEncoding() {
            return MIME.ENC_BINARY;
        }

        @Override
        public long getContentLength() {
            return -1;
        }
    }

    public void uploadObject(Key key, String dataType, Object data) {
        LOG.info("Request upload " + key + " " + dataType + " " + data.getClass().getSimpleName());
        HttpPost httpReq = makeUploadRequest(key, dataType);
        HttpEntity entity = MultipartEntityBuilder.create()
            .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
            .addPart("file", new ObjectBody(data))
            .build();
        httpReq.setEntity(entity);
        XGBoostExecRespV3 resp = executeRequestAndReturnResponse(httpReq, JsonResponseTransformer);
        assert resp.key.key().equals(key);
    }

    private <T> T executeRequestAndReturnResponse(HttpPost req, ResponseTransformer<T> transformer) {
        try (CloseableHttpClient client = clientBuilder.build();
             CloseableHttpResponse response = client.execute(req)) {
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
