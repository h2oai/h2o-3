package water.persist;

import water.H2O;
import water.Key;
import water.Value;
import water.util.FrameUtils;
import water.util.Log;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PersistEagerHTTP extends Persist {

  @Override
  public void importFiles(String path, String pattern,
          /*OUT*/ ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails, ArrayList<String> dels) {
    try {
      Key destination_key = FrameUtils.eagerLoadFromHTTP(path);
      files.add(path);
      keys.add(destination_key.toString());
    } catch (Exception e) {
      Log.err("Loading from `" + path + "` failed.", e);
      fails.add(path);
    }
  }

  @Override
  public List<String> calcTypeaheadMatches(String filter, int limit) {
    return Collections.emptyList();
  }

  /* ********************************************* */
  /* UNIMPLEMENTED methods (inspired by PersistS3) */
  /* ********************************************* */

  @Override
  public Key uriToKey(URI uri) {
    throw new UnsupportedOperationException();
  }

  // Store Value v to disk.
  @Override public void store(Value v) {
    if( !v._key.home() ) return;
    throw H2O.unimpl();         // VA only
  }

  @Override
  public void delete(Value v) {
    throw H2O.unimpl();
  }

  @Override
  public void cleanUp() {
    throw H2O.unimpl(); /* user-mode swapping not implemented */
  }

  @Override
  public byte[] load(Value v) throws IOException {
    throw H2O.unimpl();
  }

}
