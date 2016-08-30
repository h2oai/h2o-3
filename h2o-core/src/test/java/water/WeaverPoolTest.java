package water;

import javassist.ByteArrayClassPath;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import org.junit.BeforeClass;
import org.junit.Test;

public class WeaverPoolTest extends TestUtil {

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testGenClass() {
    String name = "A";
    byte[] bytecode = new byte[]{-54, -2, -70, -66, 0, 0, 0, 51, 0, 35, 1, 0, 1, 65, 7, 0, 1, 1, 0, 16, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 7, 0, 3, 1, 0, 10, 83, 111, 117, 114, 99, 101, 70, 105, 108, 101, 1, 0, 6,
      65, 46,106, 97, 118, 97, 1, 0, 12, 119, 97, 116, 101, 114, 47, 77, 82, 84, 97, 115, 107, 7, 0, 7, 1, 0, 10, 115, 101, 116, 117, 112, 76, 111, 99, 97, 108, 1, 0, 3, 40, 41, 86, 1, 0, 16, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 121, 115, 116,
      101, 109, 7, 0, 11, 1, 0, 3, 111, 117, 116, 1, 0, 21, 76, 106, 97, 118, 97, 47, 105, 111, 47, 80, 114, 105, 110, 116, 83, 116, 114, 101, 97, 109, 59, 12, 0, 13, 0, 14, 9, 0, 12, 0, 15, 1, 0, 71, 65, 115, 32, 121, 111, 117, 32, 99, 97, 110, 32, 115, 101,
      101, 32, 119, 101, 39, 118, 101, 32, 104, 97, 100, 32, 111, 117, 114, 32, 101, 121, 101, 32, 111, 110, 32, 121, 111, 117, 32, 102, 111, 114, 32, 115, 111, 109, 101, 32, 116, 105, 109, 101, 32, 110, 111, 119, 44, 32, 77, 114, 46, 32, 65, 110, 100, 101,
      114, 115, 111, 110, 8, 0, 17, 1, 0, 19, 106, 97, 118, 97, 47, 105, 111, 47, 80, 114, 105, 110, 116, 83, 116, 114, 101, 97, 109, 7, 0, 19, 1, 0, 7, 112, 114, 105, 110, 116, 108, 110, 1, 0, 21, 40, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116,
      114, 105, 110, 103, 59, 41, 86, 12, 0, 21, 0, 22, 10, 0, 20, 0, 23, 1, 0, 4, 67, 111, 100, 101, 1, 0, 6, 60, 105, 110, 105, 116, 62, 12, 0, 26, 0, 10, 10, 0, 8, 0, 27, 1, 0, 34, 40, 76, 119, 97, 116, 101, 114, 47, 72, 50, 79, 36, 72, 50, 79, 67, 111,
      117, 110, 116, 101, 100, 67, 111, 109, 112, 108, 101, 116, 101, 114, 59, 41, 86, 12, 0, 26, 0, 29, 10, 0, 8, 0, 30, 1, 0, 4, 40, 66, 41, 86, 12, 0, 26, 0, 32, 10, 0, 8, 0, 33, 0, 33, 0, 2, 0, 8, 0, 0, 0, 0, 0, 4, 0, 0, 0, 9, 0, 10, 0, 1, 0, 25, 0, 0, 0,
      21, 0, 2, 0, 1, 0, 0, 0, 9, -78, 0, 16, 18, 18, -74, 0, 24, -79, 0, 0, 0, 0, 0, 1, 0, 26, 0, 10, 0, 1, 0, 25, 0, 0, 0, 17, 0, 1, 0, 1, 0, 0, 0, 5, 42, -73, 0, 28, -79, 0, 0, 0, 0, 0, 4, 0, 26, 0, 29, 0, 1, 0, 25, 0, 0, 0, 18, 0, 2, 0, 2, 0, 0, 0, 6, 42,
      43, -73, 0, 31, -79, 0, 0, 0, 0, 0, 4, 0, 26, 0, 32, 0, 1, 0, 25, 0, 0, 0, 18, 0, 2, 0, 2, 0, 0, 0, 6, 42, 27, -73, 0, 34, -79, 0, 0, 0, 0, 0, 1, 0, 5, 0, 0, 0, 2, 0, 6};
    broadCast(name, bytecode);
    try {
      ((MRTask)Class.forName("A").newInstance()).doAllNodes();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
  }

  private static void broadCast(final String name, final byte[] b) {
    new MRTask() {
      @Override public void setupLocal() {
        ClassPool cp = Weaver.getPool();
        cp.insertClassPath( new ByteArrayClassPath(name,b));
        try {
          cp.get(name).toClass();
        } catch (CannotCompileException e) {
          e.printStackTrace();
        } catch (NotFoundException e) {
          e.printStackTrace();
        }
      }
    }.doAllNodes();
  }
}
