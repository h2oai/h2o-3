package water;

import org.junit.Ignore;

@Ignore
public class ValueHelper {

  public static byte[] rawMem(Value v) {
    return v.rawMem();
  }

  public static Object rawPojo(Value v) {
    return v.rawPOJO();
  }

}
