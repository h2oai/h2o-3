package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.fvec.Frame;

public class ImportHiveTableV99 extends RequestSchemaV3<Iced, ImportHiveTableV99> {

  //Input fields
  @API(help = "database")
  public String database = "";

  @API(help = "table", required = true)
  public String table = "";

  // Output fields
  @API(help="Parse job", direction=API.Direction.OUTPUT)
  public JobV3 job;

}
