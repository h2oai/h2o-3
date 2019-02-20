package water.api;

import water.AbstractH2OExtension;
import water.ExtensionManager;
import water.Job;
import water.api.schemas3.ImportHiveTableV99;
import water.api.schemas3.JobV3;
import water.fvec.Frame;

import java.util.Collection;

public class ImportHiveTableHandler extends Handler {
  
  public interface HiveTableImporter {
    
    String DEFAULT_DATABASE = "default";
    
    Job<Frame> loadHiveTable(String database, String tableName, String[][] partitions, boolean allowMultiFormat) throws Exception;

  }
  
  private HiveTableImporter getImporter() {
    Collection<AbstractH2OExtension> extensions = ExtensionManager.getInstance().getCoreExtensions();
    for (AbstractH2OExtension e : extensions) {
      if (e instanceof HiveTableImporter) {
        return (HiveTableImporter) e;
      }
    }
    return null;
  }
 
  @SuppressWarnings("unused") // called via reflection
  public ImportHiveTableV99 importHiveTable(int version, ImportHiveTableV99 request) throws Exception {
    HiveTableImporter importer = getImporter();
    if (importer != null) {
      try {
        Job<Frame> job = importer.loadHiveTable(request.database, request.table, request.partitions, request.allow_multi_format);
        request.job = new JobV3(job);
        return request;
      } catch (NoClassDefFoundError e) {
        throw new IllegalStateException("Hive Metastore client classes not available on classpath.", e);
      }
    } else {
      throw new IllegalStateException("HiveTableImporter extension not enabled.");
    }
  }

}
