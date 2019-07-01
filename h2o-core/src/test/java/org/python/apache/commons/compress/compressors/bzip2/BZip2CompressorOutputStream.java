package org.python.apache.commons.compress.compressors.bzip2;

import org.junit.Ignore;
import water.util.CompressionFactoryTest;

import java.io.OutputStream;

@SuppressWarnings("unused") // for CompressionFactoryTest
@Ignore // not a test ;)
public class BZip2CompressorOutputStream extends CompressionFactoryTest.DelegatingOutputStream {

  public BZip2CompressorOutputStream(OutputStream os) {
    super(os);
  }

  public String getType() {
    return "bzip2";
  }

}
