package hex.deepwater.backends;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by fmilo on 8/16/16.
 */

public class BackendParams {
    Map<String, long[]> longListValues;
    Map<String, int[]> intListValues;
    Map<String, Integer> intValues;
    Map<String, String> strValues;

    ArrayList<String> optional_args = new ArrayList<>();
    ArrayList<String> args = new ArrayList<>();

    public BackendParams(){
        strValues = new HashMap<>();
        intValues = new HashMap<>();
        intListValues = new HashMap<>();
        longListValues = new HashMap<>();
    }

    public BackendParams mustHaveInt(String name){
        args.add(name);
        intValues.put(name, -1);
        return this;
    }

    public BackendParams mustHaveString(String name){
        args.add(name);
        strValues.put(name, "");
        return this;
    }

    public BackendParams optionalList(String name, long... value){
        optional_args.add(name);
        longListValues.put(name, value);
        return this;
    }

    public BackendParams optional(String name, String value){
        optional_args.add(name);
        strValues.put(name, value);
        return this;
    }

    public BackendParams optional(String name, int value){
        optional_args.add(name);
        intValues.put(name, value);
        return this;
    }

    public BackendParams set(String name, String value ){
        strValues.put(name, value);
        return this;
    }

    public BackendParams set(String name, int value){
        intValues.put(name, value);
        return this;
    }


    public void setFloatListValues(String hidden_dropout_ratios, double[] hidden_dropout_ratios1) {

    }
}
