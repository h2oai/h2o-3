package water.util;

import com.google.gson.Gson;
import water.nbhm.NonBlockingHashMap;

public class JSONUtils {

  public static NonBlockingHashMap<String, Object> parse(String json) {
    return new Gson().fromJson(json, NonBlockingHashMap.class);
  }
}
