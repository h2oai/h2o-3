package hex.tree.xgboost.exec;

import water.Iced;
import water.util.IcedHashMap;

import java.io.Serializable;

public class XGBoostExecReq extends Iced<XGBoostExecReq> implements Serializable {
    
    public static class Init extends XGBoostExecReq {
        public int num_nodes;
        public IcedHashMap<String, Object> parms;
        public String matrix_dir_path;
        public byte[] checkpoint_bytes;
        public String featureMap;
    }

    public static class Update extends XGBoostExecReq {
        public int treeId;
    }

}
