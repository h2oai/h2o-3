package water.api;

import water.Iced;
import water.util.DocGen.HTML;

class ImportFilesV3 extends Schema<Iced,ImportFilesV3> {

  // Input fields
  @API(help="path", required=true)
  String path;

  // Output fields
  @API(help="files", direction=API.Direction.OUTPUT)
  String files[];

  @API(help="names", direction=API.Direction.OUTPUT)
  String frame_ids[];

  @API(help="fails", direction=API.Direction.OUTPUT)
  String fails[];

  @API(help="dels", direction=API.Direction.OUTPUT)
  String dels[];

  //==========================
  // Custom adapters go here

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("ImportFiles");
    if( frame_ids.length == 0 )
      ab.putStr("path",path);
    else
      ab.href("path",path, ParseSetupV3.link(frame_ids));
    ab.putAStr("files",files);
    ab.putAStr( "keys", frame_ids);
    ab.putAStr("fails",fails);
    ab.putAStr( "dels", dels);
    return ab;
  }
}
