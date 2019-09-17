package hex.genmodel.utils;

import java.io.*;

public class SerializationTestHelper {

  public static byte[] serialize(Object o) throws Exception {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutput out = new ObjectOutputStream(bos)) {
      out.writeObject(o);
      out.flush();
      return bos.toByteArray();
    }
  }

  public static Object deserialize(byte[] bs) throws Exception {
    ByteArrayInputStream bis = new ByteArrayInputStream(bs);
    try {
      ObjectInput in = new ObjectInputStream(bis);
      return in.readObject();
    } finally {
      bis.close();
    }
  }
}
