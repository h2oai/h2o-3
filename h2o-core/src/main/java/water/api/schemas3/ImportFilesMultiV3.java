package water.api.schemas3;

import water.Iced;
import water.api.API;

public class ImportFilesMultiV3 extends RequestSchemaV3<ImportFilesMultiV3.ImportFilesMulti, ImportFilesMultiV3> {

  public final static class ImportFilesMulti extends Iced<ImportFilesMulti> {
    public String[] paths;
    public String pattern;
    public String files[];
    public String destination_frames[];
    public String fails[];
    public String dels[];
  }

  // Input fields
  @API(help = "paths", required = true)
  public String[] paths;

  @API(help = "pattern", direction = API.Direction.INPUT)
  public String pattern;

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
