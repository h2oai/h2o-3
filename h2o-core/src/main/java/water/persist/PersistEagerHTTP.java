package water.persist;

import water.Key;
import water.util.FrameUtils;
import water.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PersistEagerHTTP extends EagerPersistBase {

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

}
