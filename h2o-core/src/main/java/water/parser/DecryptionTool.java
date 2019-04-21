package water.parser;

import water.DKV;
import water.Iced;
import water.Key;
import water.Keyed;
import water.fvec.ByteVec;
import water.fvec.Frame;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * Base class for implementations of the Decryption Tool
 *
 * Decryption Tool is applied to the raw data loaded from source files and decrypts them on-the-fly.
 */
public abstract class DecryptionTool extends Keyed<DecryptionTool> {

  DecryptionTool(Key<DecryptionTool> key) {
    super(key);
  }

  /**
   * Decrypts the beginning of the file and returns its clear-text binary representation.
   * @param bits the first chunk of data of the datafile. The input byte array can contain zero-bytes padding (eg. case of
   *             DEFLATE compression in Zip files, the decompressed data can be smaller than the source chunk).
   *             The implementation of the method should discard the padding (all zero bytes at the end of the array).
   * @return Decrypted binary data or the input byte array if Tool is not compatible with this input.
   */
  public abstract byte[] decryptFirstBytes(final byte[] bits);

  /**
   * Wraps the source InputStream into deciphering input stream
   * @param is InputStream created by ByteVec (H2O-specific behavior is expected!)
   * @return InputStream of decrypted data
   */
  public abstract InputStream decryptInputStream(final InputStream is);

  public boolean isTransparent() { return false; }

  /**
   * Retrieves a Decryption Tool from DKV using a given key.
   * @param key a valid DKV key or null
   * @return instance of Decryption Tool for a valid key, Null Decryption tool for a null key
   */
  public static DecryptionTool get(Key<DecryptionTool> key) {
    if (key == null)
      return new NullDecryptionTool();
    DecryptionTool decrypt = DKV.getGet(key);
    return decrypt == null ? new NullDecryptionTool() : decrypt;
  }

  /**
   * Retrieves a Secret Key using a given Decryption Setup.
   * @param ds decryption setup
   * @return SecretKey
   */
  static SecretKeySpec readSecretKey(DecryptionSetup ds) {
    Keyed<?> ksObject = DKV.getGet(ds._keystore_id);
    ByteVec ksVec = (ByteVec) (ksObject instanceof Frame ? ((Frame) ksObject).vec(0) : ksObject);
    InputStream ksStream = ksVec.openStream(null /*job key*/);
    try {
      KeyStore keystore = KeyStore.getInstance(ds._keystore_type);
      keystore.load(ksStream, ds._password);

      if (! keystore.containsAlias(ds._key_alias)) {
        throw new IllegalArgumentException("Alias for key not found");
      }

      java.security.Key key = keystore.getKey(ds._key_alias, ds._password);
      return new SecretKeySpec(key.getEncoded(), key.getAlgorithm());
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Unable to load key " + ds._key_alias + " from keystore " + ds._keystore_id, e);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read keystore " + ds._keystore_id, e);
    }
  }

  /**
   * Instantiates a Decryption Tool using a given Decryption Setup and installs it in DKV.
   * @param ds decryption setup
   * @return instance of a Decryption Tool
   */
  public static DecryptionTool make(DecryptionSetup ds) {
    if (ds._decrypt_tool_id == null)
      ds._decrypt_tool_id = Key.make();
    try {
      Class<?> dtClass = DecryptionTool.class.getClassLoader().loadClass(ds._decrypt_impl);
      if (! DecryptionTool.class.isAssignableFrom(dtClass)) {
        throw new IllegalArgumentException("Class " + ds._decrypt_impl + " doesn't implement a Decryption Tool.");
      }
      Constructor<?> constructor = dtClass.getConstructor(DecryptionSetup.class);
      DecryptionTool dt = (DecryptionTool) constructor.newInstance(ds);
      DKV.put(dt);
      return dt;
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Unknown decrypt tool: " + ds._decrypt_impl, e);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Invalid implementation of Decryption Tool (missing constructor).", e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Blueprint of the Decryption Tool
   */
  public static class DecryptionSetup extends Iced<DecryptionSetup> {
    public Key<DecryptionTool> _decrypt_tool_id; // where will be the instantiated tool installed
    public String _decrypt_impl = GenericDecryptionTool.class.getName(); // implementation
    public Key<?> _keystore_id; // where to find Java KeyStore file (Frame key or Vec key)
    public String _keystore_type; // what kind of KeyStore is used
    public String _key_alias; // what is the alias of the key in the keystore
    public char[] _password; // password to the keystore and to the keyentry
    public String _cipher_spec; // specification of the cipher (and padding)
  }

}
