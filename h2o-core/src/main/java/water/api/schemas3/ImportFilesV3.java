package water.api.schemas3;

import water.Iced;
import water.api.API;

public class ImportFilesV3 extends RequestSchemaV3<ImportFilesV3.ImportFiles, ImportFilesV3> {

  public final static class ImportFiles extends Iced {
    public String path;
    public String files[];
    public String destination_frames[];
    public String fails[];
    public String dels[];
  }

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
