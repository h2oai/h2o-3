package hex.deepwater.models;

import static org.bytedeco.javacpp.tensorflow.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by fmilo on 8/16/16.
 */
public class History {
    HashMap<String, TensorVector> arr;

    public History(){
        arr = new HashMap<>();
    }

    public History recordEvent(String name, Tensor e){
        TensorVector vec = arr.get(name);
        if (vec == null){
            vec = new TensorVector();
            arr.put(name, vec);
        }
        vec.put(e);
        return this;
    }
}
