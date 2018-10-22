package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.jdbc.SqlFetchMode;


public class ImportSQLTableV99 extends RequestSchemaV3<Iced,ImportSQLTableV99> {

  //Input fields
  @API(help = "connection_url", required = true)
  public String connection_url;

  @API(help = "table")
  public String table = "";

  @API(help = "select_query")
  public String select_query = "";

  @API(help = "username", required = true)
  public String username;

  @API(help = "password", required = true)
  public String password;

  @API(help = "columns")
  public String columns = "*";

  @API(help = "Mode for data loading. All modes may not be supported by all databases.")
  public String fetch_mode;

}
