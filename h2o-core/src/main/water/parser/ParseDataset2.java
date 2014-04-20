package water.parser;

import water.*;
import water.fvec.Frame;

public class ParseDataset2 extends Job {
  public static class ParseProgressMonitor {
    long progress() { throw H2O.unimpl(); }
  }

  static public Frame parse( Key dest, Key... srcs ) { throw H2O.unimpl(); }
}
