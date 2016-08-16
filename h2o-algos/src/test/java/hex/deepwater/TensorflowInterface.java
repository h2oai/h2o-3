package hex.deepwater;

import com.sun.javaws.exceptions.InvalidArgumentException;
import org.python.core.PyTuple;

import static org.bytedeco.javacpp.tensorflow.*;

import java.util.List;

/**
 * Created by fmilo on 8/15/16.
 */
public class TensorflowInterface {

  public static int cast_dtype(String dtype) {
    switch (dtype) {
      case "uint8": {
        return DT_UINT8;
      }
      case "float32": {
        return DT_FLOAT;
      }
      default: {
        return -1;//throw new InvalidArgumentException(new String[]{"dtype is not valid"});
      }

    }
  }



}
