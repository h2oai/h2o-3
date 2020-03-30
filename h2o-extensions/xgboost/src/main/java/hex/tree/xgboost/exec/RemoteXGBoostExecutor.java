package hex.tree.xgboost.exec;

import hex.DataInfo;
import hex.schemas.XGBoostExecReqV3;
import hex.schemas.XGBoostExecRespV3;
import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostUtils;
import hex.tree.xgboost.util.FeatureScore;
import ml.dmlc.xgboost4j.java.FrameXGBoostMatrixFactory;
import ml.dmlc.xgboost4j.java.XGBoostMatrixFactory;
import ml.dmlc.xgboost4j.java.XGBoostSaveMatrixTask;
import ml.dmlc.xgboost4j.java.XGBoostSetupTask;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import water.AutoBuffer;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.util.IcedHashMap;
import water.util.Log;

import java.io.IOException;
import java.util.Map;

import static hex.schemas.XGBoostExecReqV3.Type.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static water.util.HttpResponseStatus.OK;

public class RemoteXGBoostExecutor implements XGBoostExecutor {

    public final String uri;
    public final Key modelKey;

    public RemoteXGBoostExecutor(String remoteUri, XGBoostModel model, Frame train, XGBoostModel.XGBoostParameters parms) {
        uri = remoteUri + "/3/XGBoostExecutor";
        modelKey = model._key;
        XGBoostExecReq.Init req = new XGBoostExecReq.Init();
        XGBoostSetupTask.FrameNodes trainFrameNodes = XGBoostSetupTask.findFrameNodes(train);
        req.num_nodes = trainFrameNodes.getNumNodes();
        if (parms.hasCheckpoint()) {
            req.checkpoint_bytes = model.model_info()._boosterBytes;
        }
        DataInfo dataInfo = model.model_info().dataInfo();
        req.parms = new IcedHashMap<>();
        req.parms.putAll(XGBoostModel.createParamsMap(parms, model._output.nclasses(), dataInfo.coefNames()));
        model._output._native_parameters = BoosterParms.fromMap(req.parms).toTwoDimTable();
        XGBoostMatrixFactory f = new FrameXGBoostMatrixFactory(model, parms, trainFrameNodes);
        req.matrix_dir_path = H2O.ICE_ROOT.toString() + "/" + modelKey.toString();
        new XGBoostSaveMatrixTask(modelKey, req.matrix_dir_path, trainFrameNodes._nodes, f).run();
        req.featureMap = XGBoostUtils.createFeatureMap(model, train);
        XGBoostExecRespV3 resp = post(modelKey, XGBoostExecReqV3.Type.INIT, req);
        assert modelKey.equals(resp.key.key());
    }
    
    private XGBoostExecRespV3 post(Key key, XGBoostExecReqV3.Type type, XGBoostExecReq reqContent) {
        XGBoostExecReqV3 req = new XGBoostExecReqV3(key, type, reqContent);
        HttpPost httpReq = new HttpPost(uri);
        httpReq.setEntity(new StringEntity(req.toJsonString(), UTF_8));
        httpReq.setHeader(CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        Log.info("Remote XGBoost: Request " + type + " " + reqContent);
        try (CloseableHttpClient client = HttpClientBuilder.create().build();
             CloseableHttpResponse response = client.execute(httpReq)) {
            if (response.getStatusLine().getStatusCode() != OK.getCode()) {
                throw new IllegalStateException("Unexpected response (status: " + response.getStatusLine() + ").");
            }
            Log.info("Remote XGBoost: Response received " + response.getEntity().getContentLength() + " bytes.");
            String responseBody = EntityUtils.toString(response.getEntity());
            XGBoostExecRespV3 resp = new XGBoostExecRespV3();
            resp.fillFromBody(responseBody);
            return resp;
        } catch (IOException e) {
            throw new RuntimeException("HTTP Request failed", e);
        }
    }
    
    @Override
    public byte[] setup() {
        XGBoostExecReq req = new XGBoostExecReq(); // no req params
        XGBoostExecRespV3 resp = post(modelKey, SETUP, req);
        assert resp.key.key().equals(modelKey);
        return resp.getDataAsBytes();
    }

    @Override
    public void update(int treeId) {
        XGBoostExecReq.Update req = new XGBoostExecReq.Update();
        req.treeId = treeId;
        XGBoostExecRespV3 resp = post(modelKey, UPDATE, req);
        assert resp.key.key().equals(modelKey);
    }

    @Override
    public byte[] updateBooster() {
        XGBoostExecReq req = new XGBoostExecReq(); // no req params
        XGBoostExecRespV3 resp = post(modelKey, BOOSTER, req);
        assert resp.key.key().equals(modelKey);
        return resp.getDataAsBytes();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, FeatureScore> getFeatureScores() {
        XGBoostExecReq req = new XGBoostExecReq(); // no req params
        XGBoostExecRespV3 resp = post(modelKey, FEATURES, req);
        assert resp.key.key().equals(modelKey);
        return (Map<String, FeatureScore>) AutoBuffer.javaSerializeReadPojo(Base64.decodeBase64(resp.data));
    }

    @Override
    public void cleanup() {
        XGBoostExecReq req = new XGBoostExecReq(); // no req params
        XGBoostExecRespV3 resp = post(modelKey, BOOSTER, req);
        assert resp.key.key().equals(modelKey);
    }
}
