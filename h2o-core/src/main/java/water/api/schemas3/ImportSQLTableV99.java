package water.api.schemas3;

import water.Iced;
import water.api.API;


public class ImportSQLTableV99 extends SchemaV3<Iced,ImportSQLTableV99> {
  
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

  @API(help = "optimize")
  public boolean optimize = true;
  
}
