package water.api;

import water.ExtensionManager;
import water.Job;
import water.api.schemas3.ImportHiveTableV3;
import water.api.schemas3.JobV3;
import water.fvec.Frame;

public class ImportHiveTableHandler extends Handler {
  
  public interface HiveTableImporter {
    
    String DEFAULT_DATABASE = "default";
    String NAME = "HiveTableImporter";
    
    Job<Frame> loadHiveTable(String database, String tableName, String[][] partitions, boolean allowMultiFormat) throws Exception;

  }
  
  private HiveTableImporter getImporter() {
    return (HiveTableImporter) ExtensionManager.getInstance().getCoreExtension(HiveTableImporter.NAME);
  }
 
  @SuppressWarnings("unused") // called via reflection
  public JobV3 importHiveTable(int version, ImportHiveTableV3 request) throws Exception {
    HiveTableImporter importer = getImporter();
    if (importer != null) {
      try {
        Job<Frame> job = importer.loadHiveTable(request.database, request.table, request.partitions, request.allow_multi_format);
        return new JobV3(job);
      } catch (NoClassDefFoundError e) {
        throw new IllegalStateException("Hive Metastore client classes not available on classpath, try specifying the database as JDBC URL.", e);
      }
    } else {
      throw new IllegalStateException("HiveTableImporter extension not enabled.");
    }
  }

}
