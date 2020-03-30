package hex.schemas;

import hex.tree.xgboost.exec.XGBoostExecReq;
import org.apache.commons.codec.binary.Base64;
import water.AutoBuffer;
import water.Key;
import water.api.API;
import water.api.Schema;
import water.api.schemas3.KeyV3;

public class XGBoostExecReqV3 extends Schema<XGBoostExecReq, XGBoostExecReqV3> {
    
    public XGBoostExecReqV3(Key key, Type type, XGBoostExecReq req) {
        this.key = KeyV3.make(key);
        this.type = type;
        this.data = Base64.encodeBase64String(AutoBuffer.javaSerializeWritePojo(req));
    }

    public XGBoostExecReqV3() {
    }

    public enum Type {
        INIT, SETUP, UPDATE, BOOSTER, FEATURES, CLEANUP
    }

    @API(help="Request type", values = { "init", "setup", "update", "booster", "features", "cleanup"})
    public Type type;

    @API(help="Identifier")
    public KeyV3 key;

    @API(help="Request data stored as Base64 binary")
    public String data;

}
