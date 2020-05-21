package hex.tree.xgboost.exec;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

public class XGBoostExecReq implements Serializable {

    @Override
    public String toString() {
        return "XGBoostExecReq{}";
    }

    public static class Init extends XGBoostExecReq {
        public int num_nodes;
        public Map<String, Object> parms;
        public String matrix_dir_path;
        public String save_matrix_path;
        public String[] nodes;
        public boolean has_checkpoint;

        @Override
        public String toString() {
            return "XGBoostExecReq.Init{" +
                "num_nodes=" + num_nodes +
                ", parms=" + parms +
                ", matrix_dir_path='" + matrix_dir_path + '\'' +
                ", save_matrix_path='" + save_matrix_path + '\'' +
                ", nodes=" + Arrays.toString(nodes) +
                ", has_checkpoint=" + has_checkpoint +
                '}';
        }
    }

    public static class Update extends XGBoostExecReq {
        public int treeId;

        @Override
        public String toString() {
            return "XGBoostExecReq.Update{" +
                "treeId=" + treeId +
                '}';
        }
    }

    public static class GetMatrix extends XGBoostExecReq {
        public String matrix_dir_path;

        @Override
        public String toString() {
            return "XGBoostExecReq.GetMatrix{" +
                "matrix_dir_path='" + matrix_dir_path + '\'' +
                '}';
        }
    }

    public static class GetCheckPoint extends XGBoostExecReq {
        public String matrix_dir_path;

        @Override
        public String toString() {
            return "XGBoostExecReq.GetCheckPoint{" +
                "matrix_dir_path='" + matrix_dir_path + '\'' +
                '}';
        }
    }

}
