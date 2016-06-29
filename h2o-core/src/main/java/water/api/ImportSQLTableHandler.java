package water.api;


import water.Job;
import water.api.schemas3.ImportSQLTableV99;
import water.api.schemas3.JobV3;
import water.jdbc.SQLManager;

/**
 * Import Sql Table into H2OFrame
 */

public class ImportSQLTableHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public JobV3 importSQLTable(int version, ImportSQLTableV99 importSqlTable) {
     Job j = SQLManager.importSqlTable(importSqlTable.connection_url, importSqlTable.table, importSqlTable.select_query, 
             importSqlTable.username, importSqlTable.password, importSqlTable.columns, importSqlTable.optimize);
    return new JobV3().fillFromImpl(j);
    
  }

}
