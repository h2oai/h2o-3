package hex.deepwater.backends;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BackendParams {
  Map<String, Object> values = new HashMap<>();
  ArrayList<String> args = new ArrayList<>();

  public BackendParams set(String name, Object value ){
    args.add(name);
    values.put(name, value);
    return this;
  }

  public Object get(String name){
    return values.get(name);
  }
}
