package water.util;

import com.google.gson.Gson;
import water.nbhm.NonBlockingHashMap;

import java.util.Properties;

public class JSONUtils {

  public static NonBlockingHashMap<String, Object> parse(String json) {
    return new Gson().fromJson(json, NonBlockingHashMap.class);
  }
  public static Properties parseToProperties(String json) {
    return new Gson().fromJson(json, Properties.class);
  }
}
