package water.schemas;

import water.api.ImportFiles;
import water.Iced;
import water.H2O;
import water.H2ONode;

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

}
