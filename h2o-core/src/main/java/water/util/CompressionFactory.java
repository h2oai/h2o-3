package water.util;

import com.github.luben.zstd.ZstdOutputStream;
import water.Iced;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.zip.GZIPOutputStream;

public class CompressionFactory extends Iced<CompressionFactory> {

  private final String _name;

  private CompressionFactory(String name) {
    _name = name;
  }

  OutputStream wrapOutputStream(OutputStream os) throws IOException {
    final String n = _name.toLowerCase();
    switch (n) {
      case "none":
        return os;
      case "gzip":
        return new GZIPOutputStream(os);
      case "zstd":
        return new ZstdOutputStream(os);
      case "bzip2":
        return wrapDynamic("org.python.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream", os);
      case "snappy":
        return wrapDynamic("org.xerial.snappy.SnappyOutputStream", os);
      default:
        return wrapDynamic(_name, os);
    }
  }

  private OutputStream wrapDynamic(String className, OutputStream os) {
    try {
      Class<?> cls = Class.forName(className);
      Constructor<?> constructor = cls.getConstructor(OutputStream.class);
      return (OutputStream) constructor.newInstance(os);
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("Cannot create a compressor using class " + className, e);
    }
  }
  
  private void checkAvailability() {
    try {
      wrapOutputStream(new ByteArrayOutputStream());
    } catch (IOException e) {
      throw new IllegalStateException("Initialization failed for compression method " + _name, e);
    }
  }

  public static CompressionFactory make(String name) {
    CompressionFactory cf = new CompressionFactory(name);
    cf.checkAvailability();
    return cf;
  }

  public String getName() {
    return _name;
  }

}
