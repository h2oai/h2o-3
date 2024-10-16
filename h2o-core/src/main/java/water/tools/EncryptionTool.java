package water.tools;

import org.apache.commons.io.IOUtils;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

public class EncryptionTool {

    private File _keystore_file; // where to find Java KeyStore file
    private String _keystore_type; // what kind of KeyStore is used
    private String _key_alias; // what is the alias of the key in the keystore
    private char[] _password; // password to the keystore and to the keyentry
    private String _cipher_spec; // specification of the cipher (and padding)

    public SecretKeySpec readKey() {
        try (InputStream ksStream = new FileInputStream(_keystore_file)) {
            KeyStore keystore = KeyStore.getInstance(_keystore_type);
            keystore.load(ksStream, _password);
            if (! keystore.containsAlias(_key_alias)) {
                throw new IllegalArgumentException("Key for alias='" + _key_alias + "' not found.");
            }
            java.security.Key key = keystore.getKey(_key_alias, _password);
            return new SecretKeySpec(key.getEncoded(), key.getAlgorithm());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Unable to load key '" + _key_alias + "' from keystore '" + _keystore_file.getAbsolutePath() + "'.", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read keystore '" + _keystore_file.getAbsolutePath() + "'.", e);
        }
    }

    public void encrypt(File input, File output) throws IOException, GeneralSecurityException {
        SecretKeySpec key = readKey();
        Cipher cipher = Cipher.getInstance(_cipher_spec);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        try (FileInputStream inputStream = new FileInputStream(input);
             FileOutputStream outputStream = new FileOutputStream(output);
             CipherOutputStream cipherStream = new CipherOutputStream(outputStream, cipher);
        ) {
            IOUtils.copyLarge(inputStream, cipherStream);
        }
    }

    public static void main(String[] args) throws GeneralSecurityException, IOException {
        mainInternal(args);
    }
    public static void mainInternal(String[] args) throws GeneralSecurityException, IOException {
        EncryptionTool et = new EncryptionTool();
        et._keystore_file = new File(args[0]);
        et._keystore_type = args[1];
        et._key_alias = args[2];
        et._password = args[3].toCharArray();
        et._cipher_spec = args[4];

        et.encrypt(new File(args[5]), new File(args[6]));
    }

}
