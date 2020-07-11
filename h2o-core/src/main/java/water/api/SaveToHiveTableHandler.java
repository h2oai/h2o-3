package water.api;

import water.ExtensionManager;
import water.Key;
import water.api.schemas3.SaveToHiveTableV3;
import water.fvec.Frame;

public class SaveToHiveTableHandler extends Handler {

    public interface HiveFrameSaver {

        String NAME = "HiveFrameSaver";
        
        enum Format { CSV, PARQUET }

        void saveFrameToHive(
            Key<Frame> frameKey,
            String jdbcUrl,
            String tableName,
            Format format,
            String tablePath,
            String tmpPath
        );

    }

    private HiveFrameSaver getSaver() {
        return (HiveFrameSaver) ExtensionManager.getInstance().getCoreExtension(HiveFrameSaver.NAME);
    }

    @SuppressWarnings("unused") // called via reflection
    public SaveToHiveTableV3 saveToHiveTable(int version, SaveToHiveTableV3 request) {
        HiveFrameSaver saver = getSaver();
        if (saver != null) {
            saver.saveFrameToHive(
                request.frame_id.key(),
                request.jdbc_url,
                request.table_name,
                request.format,
                request.table_path,
                request.tmp_path
            );
            return request;
        } else {
            throw new IllegalStateException("HiveTableSaver extension not enabled.");
        }
    }

}
