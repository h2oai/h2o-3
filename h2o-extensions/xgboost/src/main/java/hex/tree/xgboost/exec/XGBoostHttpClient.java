package hex.tree.xgboost.exec;

import hex.genmodel.utils.IOUtils;
import hex.schemas.XGBoostExecReqV3;
import hex.schemas.XGBoostExecRespV3;
import hex.tree.xgboost.remote.RemoteXGBoostUploadServlet;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import water.Key;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static water.util.HttpResponseStatus.OK;

public class XGBoostHttpClient {

    private static final Logger LOG = Logger.getLogger(XGBoostHttpClient.class);

    private final String baseUri;
    private final HttpClientBuilder clientBuilder;
    private final UsernamePasswordCredentials credentials;

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
        String suffix = "3/XGBoostExecutor.";
        if (!baseUri.endsWith("/")) suffix = "/" + suffix;
        this.baseUri = (https ? "https" : "http") + "://" + baseUri + suffix;
        if (userName != null) {
            credentials = new UsernamePasswordCredentials(userName, password);
        } else {
            credentials = null;
        }
        this.clientBuilder = createClientBuilder(https);
    }

    private HttpClientBuilder createClientBuilder(boolean https) {
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
            if (credentials != null) {
                CredentialsProvider provider = new BasicCredentialsProvider();
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
    
    private HttpPost makeUploadRequest(Key key, RemoteXGBoostUploadServlet.RequestType dataType) {
        try {
            URIBuilder uri = new URIBuilder(baseUri + "upload");
            uri.setParameter("model_key", key.toString())
                .setParameter("data_type", dataType.toString());
            return new HttpPost(uri.build());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to build request URI.", e);
        }
    }
    
    public void uploadBytes(Key key, RemoteXGBoostUploadServlet.RequestType dataType, byte[] data) {
        LOG.info("Request upload " + key + " " + dataType + " " + data.length + " bytes");
        HttpPost httpReq = makeUploadRequest(key, dataType);
        httpReq.setEntity(new InputStreamEntity(new ByteArrayInputStream(data)));
        addAuthentication(httpReq);
        XGBoostExecRespV3 resp = executeRequestAndReturnResponse(httpReq, JsonResponseTransformer);
        assert resp.key.key().equals(key);
    }
    
    private static class ObjectEntity extends AbstractHttpEntity {
        
        private final Object object;

        private ObjectEntity(Object object) {
            this.object = object;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            LOG.debug("Sending " + object);
            ObjectOutputStream os = new ObjectOutputStream(out);
            os.writeObject(object);
            os.flush();
        }

        @Override
        public boolean isStreaming() {
            return true;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public InputStream getContent() throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }
    }

    public void uploadObject(Key key, RemoteXGBoostUploadServlet.RequestType dataType, Object data) {
        LOG.info("Request upload " + key + " " + dataType + " " + data.getClass().getSimpleName());
        HttpPost httpReq = makeUploadRequest(key, dataType);
        httpReq.setEntity(new ObjectEntity(data));
        addAuthentication(httpReq);
        XGBoostExecRespV3 resp = executeRequestAndReturnResponse(httpReq, JsonResponseTransformer);
        assert resp.key.key().equals(key);
    }

    /*
    For binary POST requests its necessary to add auth this way 
     */
    private void addAuthentication(HttpPost httpReq) {
        if (credentials != null) {
            try {
                httpReq.addHeader(new BasicScheme().authenticate(credentials, httpReq, null));
            } catch (AuthenticationException e) {
                throw new IllegalStateException("Unable to authenticate request.", e);
            }
        }
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
