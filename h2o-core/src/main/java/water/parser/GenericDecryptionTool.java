package water.parser;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

public class GenericDecryptionTool extends DecryptionTool {

  private final byte[] _encoded_key;
  private final String _key_algo;
  private final String _cipher_spec;

  public GenericDecryptionTool(DecryptionSetup ds) {
    super(ds._decrypt_tool_id);
    SecretKeySpec secretKey = readSecretKey(ds);
    _key_algo = secretKey.getAlgorithm();
    _encoded_key = secretKey.getEncoded();
    _cipher_spec = ds._cipher_spec;
  }

  @Override
  public byte[] decryptFirstBytes(final byte[] bits) {
    Cipher cipher = createDecipherer();

    final int bs = cipher.getBlockSize();
    int len = bits.length;
    if (((bs > 0) && (len % bs != 0)) || bits[len - 1] == 0) {
      while (len > 0 && bits[len - 1] == 0)
        len--;
      len = bs > 0 ? len - (len % bs) : len;
    }

    return cipher.update(bits, 0, len);
  }

  @Override
  public InputStream decryptInputStream(final InputStream is) {
    Cipher cipher = createDecipherer();
    return new CipherInputStream(is, cipher) {
      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) { // Back-channel read of chunk idx (delegated to the original InputStream)
          return is.read(null, off, len);
        }
        return super.read(b, off, len);
      }
      @Override
      public int available() throws IOException {
        int avail = super.available();
        // H2O's contract with the available() method differs from the contract specified by InputStream
        // we need to make sure we return a positive number (if we don't have anything in the buffer - ask the original IS)
        return avail > 0 ? avail : is.available();
      }
    };
  }

  private Cipher createDecipherer() {
    SecretKeySpec secKeySpec = new SecretKeySpec(_encoded_key, _key_algo);
    try {
      Cipher cipher = Cipher.getInstance(_cipher_spec);
      cipher.init(Cipher.DECRYPT_MODE, secKeySpec);
      return cipher;
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Cipher initialization failed", e);
    }
  }

}
