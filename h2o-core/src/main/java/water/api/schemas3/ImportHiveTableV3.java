package water.api.schemas3;

import water.Iced;
import water.api.API;

public class ImportHiveTableV3 extends RequestSchemaV3<Iced, ImportHiveTableV3> {

  //Input fields
  @API(help = "database")
  public String database = "";

  @API(help = "table", required = true)
  public String table = "";

  @API(help = "partitions")
  public String[][] partitions;

  @API(help = "partitions")
  public boolean allow_multi_format = false;

}
