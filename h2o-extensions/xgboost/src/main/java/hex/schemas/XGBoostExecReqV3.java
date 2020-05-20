package hex.schemas;

import hex.tree.xgboost.exec.XGBoostExecReq;
import org.apache.commons.codec.binary.Base64;
import water.AutoBuffer;
import water.Iced;
import water.Key;
import water.api.API;
import water.api.Schema;
import water.api.schemas3.KeyV3;

public class XGBoostExecReqV3 extends Schema<Iced, XGBoostExecReqV3> {
    
    public XGBoostExecReqV3(Key key, XGBoostExecReq req) {
        this.key = KeyV3.make(key);
        this.data = Base64.encodeBase64String(AutoBuffer.javaSerializeWritePojo(req));
    }

    public XGBoostExecReqV3() {
    }

    @API(help="Identifier")
    public KeyV3 key;

    @API(help="Request data stored as Base64 binary")
    public String data;

    @SuppressWarnings("unchecked")
    public <T> T readReq() {
        return (T) AutoBuffer.javaSerializeReadPojo(Base64.decodeBase64(data));
    }


}
