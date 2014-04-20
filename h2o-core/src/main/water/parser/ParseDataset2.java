package water.parser;

import water.*;
import water.fvec.*;

public class ParseDataset2 extends Job<Frame> {
  public static class ParseProgressMonitor {
    long progress() { throw H2O.unimpl(); }
  }

  // Keys are limited to ByteVec Keys and Frames-of-1-ByteVec Keys
  public static Frame parse(Key okey, Key... keys) { return parse(okey,keys,true, true); }

  // Guess setup from inspecting the first Key only, then parse.
  // Suitable for e.g. testing setups, where the data is known to be sane.
  // NOT suitable for random user input!
  public static Frame parse(Key okey, Key[] keys, boolean delete_on_done, boolean checkHeader) {
    Key k = keys[0];
    byte[] bits = ZipUtil.getFirstUnzippedBytes(getByteVec(k));
    ParserSetup globalSetup = ParserSetup.guessSetup(bits, checkHeader);
    if( globalSetup._ncols == 0 ) throw new java.lang.IllegalArgumentException(globalSetup.toString());
    return forkParseDataset(okey, keys, globalSetup, delete_on_done).get();
  }

  // Allow both ByteVec keys and Frame-of-1-ByteVec
  private static ByteVec getByteVec(Key key) {
    Iced ice = DKV.get(key).get();
    return (ByteVec)(ice instanceof ByteVec ? ice : ((Frame)ice).vecs()[0]);
  }

  private static ParseDataset2 forkParseDataset( Key okey, Key[] keys, ParserSetup globalSetup, boolean delete_on_done ) {
    throw H2O.unimpl();
  }
}
