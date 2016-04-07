package water.api;


import water.jdbc.SQLManager;

/**
 * Import Sql Table into H2OFrame
 */

public class ImportSQLTableHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public JobV3 importSQLTable(int version, ImportSQLTableV99 importSqlTable) {
     return SQLManager.importSqlTable(importSqlTable.database_sys, importSqlTable.host, importSqlTable.port, 
            importSqlTable.database, importSqlTable.table, importSqlTable.username, importSqlTable.password);
    
  }

}
