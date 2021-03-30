package water.parser;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.api.schemas3.ParseSetupV3;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FileUtils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.*;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class ParseTestEncrypted extends TestUtil {

  @ClassRule
  public static TemporaryFolder tmp = new TemporaryFolder();

  private static String PLAINTEXT_FILE = "smalldata/demos/citibike_20k.csv";

  private static String KEYSTORE_TYPE = "JCEKS"; // Note: need to use JCEKS, default JKS cannot store non-private keys!
  private static String MY_CIPHER_SPEC = "AES/ECB/PKCS5Padding";
  private static char[] MY_PASSWORD = "Password123".toCharArray();
  private static String MY_KEY_ALIAS = "secretKeyAlias";

  private static File _jks;

  @Parameterized.Parameter
  public String _encrypted_name;

  @Parameterized.Parameters
  public static Iterable<? extends Object> data() { 
    return Arrays.asList("encrypted.csv.aes", "encrypted.zip.aes", "encrypted.gz.aes"); 
  }

  @BeforeClass
  public static void setup() throws Exception {
    SecretKey secretKey = generateSecretKey();
    // KeyStore
    _jks = writeKeyStore(secretKey);
    // Encrypted CSV
    writeEncrypted(FileUtils.getFile(PLAINTEXT_FILE), tmp.newFile("encrypted.csv.aes"), secretKey);
    // CSV in an Encrypted Zip container
    writeEncryptedZip(FileUtils.getFile(PLAINTEXT_FILE), tmp.newFile("encrypted.zip.aes"), secretKey);
    // CSV in an Encrypted Gzip container
    writeEncryptedGzip(FileUtils.getFile(PLAINTEXT_FILE), tmp.newFile("encrypted.gz.aes"), secretKey);

    TestUtil.stall_till_cloudsize(1);
  }

  private static SecretKey generateSecretKey() throws Exception {
    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
    keyGen.init(128);
    return keyGen.generateKey();
  }

  private static File writeKeyStore(SecretKey secretKey) throws Exception {
    KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
    ks.load(null);
    KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(MY_PASSWORD);

    KeyStore.SecretKeyEntry skEntry = new KeyStore.SecretKeyEntry(secretKey);
    ks.setEntry(MY_KEY_ALIAS, skEntry, protParam);

    File jks = tmp.newFile("mykeystore.jks");
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(jks);
      ks.store(fos, MY_PASSWORD);
      fos.close();
    } finally {
      IOUtils.closeQuietly(fos);
    }
    return jks;
  }

  private static void writeEncrypted(File source, File target, SecretKey secretKey) throws Exception {
    FileOutputStream fileOut = new FileOutputStream(target);
    try {
      encryptFile(source, fileOut, secretKey);
    } finally {
      IOUtils.closeQuietly(fileOut);
    }
  }

  private static void writeEncryptedGzip(File source, File target, SecretKey secretKey) throws Exception {
    File tmpFile = tmp.newFile();
    try (InputStream sourceIn = new FileInputStream(source); 
         FileOutputStream fileOut = new FileOutputStream(tmpFile);
         GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOut)) {
      IOUtils.copyLarge(sourceIn, gzipOutput);
    }
    writeEncrypted(tmpFile, target, secretKey);
  }

  private static void writeEncryptedZip(File source, File target, SecretKey secretKey) throws Exception {
    File tmpFile = tmp.newFile();
    FileOutputStream tmpOut = new FileOutputStream(tmpFile);
    try {
      ZipOutputStream zipOut = new ZipOutputStream(tmpOut);
      ZipEntry ze = new ZipEntry(source.getName());
      zipOut.putNextEntry(ze);
      try (InputStream sourceIn = new FileInputStream(source)) {
        IOUtils.copyLarge(sourceIn, zipOut);
      }
      zipOut.closeEntry();
      zipOut.close();
    } finally {
      IOUtils.closeQuietly(tmpOut);
    }
    writeEncrypted(tmpFile, target, secretKey);
  }

  private static void encryptFile(File source, OutputStream outputStream, SecretKey secretKey) throws Exception {
    FileInputStream inputStream = new FileInputStream(source);
    try {
      Cipher cipher = Cipher.getInstance(MY_CIPHER_SPEC);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey);

      byte[] inputBytes = new byte[(int) source.length()];
      IOUtils.readFully(inputStream, inputBytes);
      byte[] outputBytes = cipher.doFinal(inputBytes);
      outputStream.write(outputBytes);

      inputStream.close();
    } finally {
      IOUtils.closeQuietly(inputStream);
    }
  }

  @Test
  public void testParseEncrypted() {
    Scope.enter();
    try {
      // 1. Upload the Keystore file
      Vec jksVec = Scope.track(makeNfsFileVec(_jks.getAbsolutePath()));

      // 2. Set Decryption Tool Parameters
      DecryptionTool.DecryptionSetup ds = new DecryptionTool.DecryptionSetup();
      ds._decrypt_tool_id = Key.make("aes_decrypt_tool");
      ds._keystore_id = jksVec._key;
      ds._key_alias = MY_KEY_ALIAS;
      ds._keystore_type = KEYSTORE_TYPE;
      ds._password = MY_PASSWORD;
      ds._cipher_spec = MY_CIPHER_SPEC;

      // 3. Instantiate & Install the Decryption Tool into DKV
      Keyed<DecryptionTool> dt = Scope.track_generic(DecryptionTool.make(ds));

      // 4. Load encrypted file into a ByteVec
      Vec encVec = Scope.track(makeNfsFileVec(new File(tmp.getRoot(), _encrypted_name).getAbsolutePath()));

      // 5. Create Parse Setup with a given Decryption Tool
      ParseSetup ps = new ParseSetup(new ParseSetupV3()).setDecryptTool(dt._key);
      ParseSetup guessedSetup = ParseSetup.guessSetup(new Key[]{encVec._key}, ps);
      assertEquals("aes_decrypt_tool", guessedSetup._decrypt_tool.toString());
      assertEquals("CSV", guessedSetup._parse_type.name());

      // 6. Parse encrypted dataset
      Key<Frame> fKey = Key.make("decrypted_frame");
      Frame decrypted = Scope.track(ParseDataset.parse(fKey, new Key[]{encVec._key}, false, guessedSetup));

      // 7. Compare with source dataset
      Frame plaintext = Scope.track(parseTestFile(PLAINTEXT_FILE));
      assertArrayEquals(plaintext._names, decrypted._names);
      for (String n : plaintext._names) {
        switch (plaintext.vec(n).get_type_str()) {
          case "String":
            assertStringVecEquals(plaintext.vec(n), decrypted.vec(n));
            break;
          case "Enum":
            assertCatVecEquals(plaintext.vec(n), decrypted.vec(n));
            break;
          default:
            assertVecEquals(plaintext.vec(n), decrypted.vec(n), 0.001);
        }
      }
    } finally {
      Scope.exit();
    }
  }

}
