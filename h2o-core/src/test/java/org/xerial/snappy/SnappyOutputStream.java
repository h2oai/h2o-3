package org.xerial.snappy;

import org.junit.Ignore;
import water.util.CompressionFactoryTest;

import java.io.OutputStream;

@SuppressWarnings("unused") // for CompressionFactoryTest
@Ignore // not a test ;)
public class SnappyOutputStream extends CompressionFactoryTest.DelegatingOutputStream {

  public SnappyOutputStream(OutputStream os) {
    super(os);
  }

  public String getType() {
    return "snappy";
  }

}
