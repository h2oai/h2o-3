package water.util;

import com.github.luben.zstd.ZstdInputStream;
import water.Iced;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.zip.GZIPInputStream;

public class DecompressionFactory extends Iced<DecompressionFactory> {

  private final String _name;

  private DecompressionFactory(String name) {
    _name = name;
  }

  InputStream wrapInputStream(InputStream is) throws IOException {
    final String n = _name.toLowerCase();
    switch (n) {
      case "none":
        return is;
      case "gzip":
        return new GZIPInputStream(is);
      case "bzip2":
        return wrapDynamic("org.python.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream", is);
      case "snappy":
        return wrapDynamic("org.xerial.snappy.SnappyInputStream", is);
      case "zstd":
        return new ZstdInputStream(is);
      default:
        return wrapDynamic(_name, is);
    }
  }

  private InputStream wrapDynamic(String className, InputStream os) {
    try {
      Class<?> cls = Class.forName(className);
      Constructor<?> constructor = cls.getConstructor(InputStream.class);
      return (InputStream) constructor.newInstance(os);
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("Cannot create a decompressor using class " + className, e);
    }
  }

  public static DecompressionFactory make(String name) {
    return new DecompressionFactory(name);
  }

}
