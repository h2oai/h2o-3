package water.api.schemas3;

import water.Iced;
import water.api.API;

public class ImportFilesV3 extends SchemaV3<Iced,ImportFilesV3> {

  // Input fields
  @API(help = "path", required = true)
  public String path;

  // Output fields
  @API(help = "files", direction = API.Direction.OUTPUT)
  public String files[];

  @API(help = "names", direction = API.Direction.OUTPUT)
  public String destination_frames[];

  @API(help = "fails", direction = API.Direction.OUTPUT)
  public String fails[];

  @API(help = "dels", direction = API.Direction.OUTPUT)
  public String dels[];
  
}
