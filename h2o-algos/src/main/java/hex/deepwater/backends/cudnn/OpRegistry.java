package hex.deepwater.backends.cudnn;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by fmilo on 8/19/16.
 */
public class OpRegistry {

    static Map<String, Operation> global = new HashMap<>();

    static protected void register(String name, Operation op){
       global.put(name, op);
    }

    static protected Operation get(String name){
        return global.get(name);
    }
}
