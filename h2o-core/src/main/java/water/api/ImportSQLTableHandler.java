package water.api;


import water.Job;
import water.jdbc.SQLManager;

/**
 * Import Sql Table into H2OFrame
 */

public class ImportSQLTableHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public JobV3 importSQLTable(int version, ImportSQLTableV99 importSqlTable) {
     Job j = SQLManager.importSqlTable(importSqlTable.database_sys, importSqlTable.database, importSqlTable.table, 
             importSqlTable.username, importSqlTable.password, importSqlTable.host, importSqlTable.port,
             importSqlTable.optimize);
    return new JobV3().fillFromImpl(j);
    
  }

}
