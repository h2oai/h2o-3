package hex.schemas;

import water.BootstrapFreezable;
import org.apache.commons.codec.binary.Base64;
import water.AutoBuffer;
import water.Iced;
import water.Key;
import water.api.API;
import water.api.Schema;
import water.api.schemas3.KeyV3;

public class XGBoostExecRespV3 extends Schema<Iced, XGBoostExecRespV3> {

    @API(help="Identifier")
    public KeyV3 key;

    @API(help="Arbitrary response data stored as Base64 encoded binary")
    public String data;

    public XGBoostExecRespV3() {}
    
    public XGBoostExecRespV3(Key key) {
        this.key = KeyV3.make(key);
        this.data = "";
    }

    public XGBoostExecRespV3(Key key, BootstrapFreezable<?> data) {
        this.key = KeyV3.make(key);
        this.data = Base64.encodeBase64String(AutoBuffer.serializeBootstrapFreezable(data));
    }

    @Override
    public String toString() {
        return "XGBoostExecRespV3{" +
            "key=" + key +
            '}';
    }

    public <T> T readData() {
        if (data.length() > 0) {
            return (T) AutoBuffer.deserializeBootstrapFreezable(Base64.decodeBase64(data));
        } else {
            return null;
        }
    }
}
