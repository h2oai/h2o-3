package water.udf;

import water.Scope;
import water.fvec.Vec;

import java.lang.reflect.Method;

public class UdfUtils {
    public static <T> T willDrop(T vh) {
      try { // using reflection so that Paula Bean's code is intact
        Method vec = vh.getClass().getMethod("vec");
        Scope.track((Vec)vec.invoke(vh));
      } catch (Exception e) {
        // just ignore
      }
      return vh;
    }
}
