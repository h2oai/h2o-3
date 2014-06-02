package water.schemas;

import java.util.Arrays;
import water.api.ImportFiles;
import water.util.DocGen.HTML;

public class ImportFilesV1 extends Schema<ImportFiles,ImportFilesV1> {

  // Input fields
  @API(help="path", validation="/*path is required*/")
  String path;

  // Output fields
  @API(help="files")
  String files[];

  @API(help="keys")
  String keys[];

  @API(help="fails")
  String fails[];

  @API(help="dels")
  String dels[];

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public ImportFilesV1 fillInto( ImportFiles h ) {
    h._path = path;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override public ImportFilesV1 fillFrom( ImportFiles h ) {
    files = h._files;
    keys  = h._keys ;
    fails = h._fails;
    dels  = h._dels ;
    return this;
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("ImportFiles");
    if( keys.length==0 ) ab.putStr("path",path);
    else ab.href("path",path,ParseV2.link(keys));
    ab.putAStr("files",files);
    ab.putAStr( "keys", keys);
    ab.putAStr("fails",fails);
    ab.putAStr( "dels", dels);
    return ab;
  }
}
