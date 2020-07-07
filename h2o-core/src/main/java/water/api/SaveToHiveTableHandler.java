package water.api;

import water.AbstractH2OExtension;
import water.ExtensionManager;
import water.Key;
import water.api.schemas3.SaveToHiveTableV3;
import water.fvec.Frame;

import java.util.Collection;

public class SaveToHiveTableHandler extends Handler {

    public interface HiveFrameSaver {

        void saveFrameToHive(Key<Frame> frameKey, String jdbcUrl, String tableName, String tmpPath);

    }

    private HiveFrameSaver getSaver() {
        Collection<AbstractH2OExtension> extensions = ExtensionManager.getInstance().getCoreExtensions();
        for (AbstractH2OExtension e : extensions) {
            if (e instanceof HiveFrameSaver) {
                return (HiveFrameSaver) e;
            }
        }
        return null;
    }

    @SuppressWarnings("unused") // called via reflection
    public SaveToHiveTableV3 saveToHiveTable(int version, SaveToHiveTableV3 request) {
        HiveFrameSaver saver = getSaver();
        if (saver != null) {
            saver.saveFrameToHive(request.frame_id.key(), request.jdbc_url, request.table_name, request.tmp_path);
            return request;
        } else {
            throw new IllegalStateException("HiveTableSaver extension not enabled.");
        }
    }

}
