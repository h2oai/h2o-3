package water.parser;

import water.Key;

import java.io.InputStream;

public class NullDecryptionTool extends DecryptionTool {

  public NullDecryptionTool() {
    super(null);
  }

  @Override
  public byte[] decryptFirstBytes(byte[] bits) {
    return bits;
  }

  @Override
  public InputStream decryptInputStream(InputStream is) {
    return is;
  }

  @Override
  public boolean isTransparent() {
    return true;
  }

}
