package hex.schemas;

import water.Iced;
import water.Key;
import water.api.API;
import water.api.Schema;
import water.api.schemas3.KeyV3;

public class XGBoostExecRespV3 extends Schema<Iced, XGBoostExecRespV3> {

    public XGBoostExecRespV3() {}
    
    public XGBoostExecRespV3(Key key) {
        this.key = KeyV3.make(key);
    }

    @API(help="Identifier")
    public KeyV3 key;

}
