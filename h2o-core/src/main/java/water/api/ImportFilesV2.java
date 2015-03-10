package water.api;

import water.Iced;
import water.util.DocGen.HTML;

class ImportFilesV2 extends Schema<Iced,ImportFilesV2> {

  // Input fields
  @API(help="path", required=true)
  String path;

  // Output fields
  @API(help="files", direction=API.Direction.OUTPUT)
  String files[];

  @API(help="keys", direction=API.Direction.OUTPUT)
  String keys[];

  @API(help="fails", direction=API.Direction.OUTPUT)
  String fails[];

  @API(help="dels", direction=API.Direction.OUTPUT)
  String dels[];

  //==========================
  // Custom adapters go here

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("ImportFiles");
    if( keys.length == 0 )
      ab.putStr("path",path);
    else
      ab.href("path",path, ParseSetupV2.link(keys));
    ab.putAStr("files",files);
    ab.putAStr( "keys", keys);
    ab.putAStr("fails",fails);
    ab.putAStr( "dels", dels);
    return ab;
  }
}
