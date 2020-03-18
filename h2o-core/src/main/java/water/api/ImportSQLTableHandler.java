package water.api;


import water.Job;
import water.api.schemas3.ImportSQLTableV99;
import water.api.schemas3.JobV3;
import water.jdbc.SQLManager;
import water.jdbc.SqlFetchMode;
import water.util.EnumUtils;

/**
 * Import Sql Table into H2OFrame
 */

public class ImportSQLTableHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public JobV3 importSQLTable(int version, final ImportSQLTableV99 importSqlTable) {
    final SqlFetchMode sqlFetchMode;
    if (importSqlTable.fetch_mode == null) {
      sqlFetchMode = SqlFetchMode.DISTRIBUTED;
    } else {
      sqlFetchMode = EnumUtils.valueOfIgnoreCase(SqlFetchMode.class, importSqlTable.fetch_mode)
              .orElseThrow(() -> new IllegalArgumentException("Unrecognized SQL Fetch mode: " + importSqlTable.fetch_mode));
    }
    Boolean useTempTable = null;
    if (importSqlTable.use_temp_table != null) {
      useTempTable = Boolean.parseBoolean(importSqlTable.use_temp_table);
    }
    Job j = SQLManager.importSqlTable(
        importSqlTable.connection_url, importSqlTable.table, importSqlTable.select_query,
        importSqlTable.username, importSqlTable.password, importSqlTable.columns,
        useTempTable, importSqlTable.temp_table_name,
        sqlFetchMode, importSqlTable.num_chunks_hint != null ? Integer.valueOf(importSqlTable.num_chunks_hint) : null
    );
    return new JobV3().fillFromImpl(j);

  }

}
