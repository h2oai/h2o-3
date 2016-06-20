package water.api;

import water.Iced;

class ImportFilesV3 extends SchemaV3<Iced,ImportFilesV3> {

  // Input fields
  @API(help="path", required=true)
  String path;

  // Output fields
  @API(help="files", direction=API.Direction.OUTPUT)
  String files[];

  @API(help="names", direction=API.Direction.OUTPUT)
  String destination_frames[];

  @API(help="fails", direction=API.Direction.OUTPUT)
  String fails[];

  @API(help="dels", direction=API.Direction.OUTPUT)
  String dels[];
  
}
