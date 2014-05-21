package water.schemas;

import water.api.ImportFiles;
import water.H2O;
import water.H2ONode;

public class ImportFilesV1 extends Schema<ImportFiles,ImportFilesV1> {

  // Input fields
  private final Inputs _ins = new Inputs();
  private static class Inputs {
    @API(help="path", validation="/*path is required*/") 
    String path;
  }

  // Output fields
  private final Outputs _outs = new Outputs();
  private static class Outputs {
    @API(help="files")
    String files[];

    @API(help="keys")
    String keys[];

    @API(help="fails")
    String fails[];

    @API(help="dels")
    String dels[];
  }

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public ImportFilesV1 fillInto( ImportFiles h ) {
    // path is required
    throw H2O.unimpl();
  }

  // Version&Schema-specific filling from the handler
  @Override public ImportFilesV1 fillFrom( ImportFiles h ) {
    throw H2O.unimpl();
  }

}
