package hex.deepwater;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.python.core.PyObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by fmilo on 8/16/16.
 */

public class ParameterBuilder {
    Map<String, long[]> longListValues;
    Map<String, int[]> intListValues;
    Map<String, Integer> intValues;
    Map<String, String> strValues;

    ArrayList<String> optional_args = new ArrayList<>();
    ArrayList<String> args = new ArrayList<>();

    public ParameterBuilder(){
        strValues = new HashMap<>();
        intValues = new HashMap<>();
        intListValues = new HashMap<>();
        longListValues = new HashMap<>();
    }

    public ParameterBuilder mustHaveInt(String name){
        args.add(name);
        intValues.put(name, -1);
        return this;
    }

    public ParameterBuilder mustHaveString(String name){
        args.add(name);
        strValues.put(name, "");
        return this;
    }

    public ParameterBuilder optionalList(String name, long... value){
        optional_args.add(name);
        longListValues.put(name, value);
        return this;
    }

    public ParameterBuilder optional(String name, String value){
        optional_args.add(name);
        strValues.put(name, value);
        return this;
    }

    public ParameterBuilder optional(String name, int value){
        optional_args.add(name);
        intValues.put(name, value);
        return this;
    }

    public void mustHave(String name){

    }

    public ParameterBuilder set(String name, String value ){
        strValues.put(name, value);
        return this;
    }

    public ParameterBuilder set(String name, int value){
        intValues.put(name, value);
        return this;
    }

    public ParameterBuilder parsePython(PyObject[] arg_values, String[] kwds  ){
        int index = 0;
        for(String arg: args){
            if (intValues.containsKey(arg)) {
                if (arg_values.length > index){
                    intValues.put(arg, arg_values[index].asInt());
                    index++;
                    continue;
                }
            }
            if (strValues.containsKey(arg)) {
                if (arg_values.length > index){
                    strValues.put(arg, arg_values[index].asString());
                    index++;
                    continue;
                }
            }

            throw new IllegalArgumentException("arg not found");
        }
        // parse optional
        for(String arg: kwds){
            if (intValues.containsKey(arg)) {
                if (arg_values.length > index){
                    intValues.put(arg, arg_values[index].asInt());
                    index++;
                    continue;
                }
            }
            if (strValues.containsKey(arg)) {
                if (arg_values.length > index){
                    strValues.put(arg, arg_values[index].asString());
                    index++;
                    continue;
                }
            }
            if (longListValues.containsKey(arg)) {
                if (arg_values.length > index){
                    longListValues.put(arg, asLongArray(arg_values[index]));
                    index++;
                    continue;
                }
            }
            if (intListValues.containsKey(arg)) {
                if (arg_values.length > index){
                    intListValues.put(arg, asIntArray(arg_values[index]));
                    index++;
                    continue;
                }
            }

            throw new IllegalArgumentException(" unknown argument "+arg );
        }

        return this;
    }

    public long[] getLongList(String name){
      return longListValues.get(name);
    }

    public long[] asLongArray(PyObject o){
        ArrayList<Long> r = new ArrayList<>();
        for(PyObject obj: o.asIterable()){
            r.add(obj.asLong());
        }
        return Longs.toArray(r);
    }

    public int[] asIntArray(PyObject o){
        ArrayList<Integer> r = new ArrayList<>();
        for(PyObject obj: o.asIterable()){
            r.add(obj.asInt());
        }
        return Ints.toArray(r);
    }
}
