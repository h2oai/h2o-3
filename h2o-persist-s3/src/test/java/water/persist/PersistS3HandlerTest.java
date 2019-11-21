package water.persist;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.DKV;
import water.Iced;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;

import java.util.ArrayList;
import java.util.UUID;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class PersistS3HandlerTest extends TestUtil {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(5);
    }


    PersistS3Handler persistS3Handler;
    private static final String AWS_ACCESS_KEY_PROPERTY_NAME = "AWS_ACCESS_KEY_ID";
    private static final String AWS_SECRET_KEY_PROPERTY_NAME = "AWS_SECRET_ACCESS_KEY";
    private static final String IRIS_H2O_AWS = "s3://test.0xdata.com/h2o-unit-tests/iris.csv";

    @Before
    public void setUp() throws Exception {
        persistS3Handler = new PersistS3Handler();
    }


    @Test
    public void setS3Credentials() {
        // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
        final String accessKeyId = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
        final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);
        assumeTrue(accessKeyId != null);
        assumeTrue(secretKey != null);
        
        final Key credentialsKey= Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);
        PersistS3 persistS3 = new PersistS3();
        final ArrayList<String> keys = new ArrayList<>();
        final ArrayList<String> fails = new ArrayList<>();
        final ArrayList<String> deletions = new ArrayList<>();
        final ArrayList<String> files = new ArrayList<>();

        try {
            final PersistS3CredentialsV3 persistS3CredentialsV3 = new PersistS3CredentialsV3();
            persistS3CredentialsV3.secret_key_id = accessKeyId;
            persistS3CredentialsV3.secret_access_key =  secretKey;
            persistS3Handler.setS3Credentials(3, persistS3CredentialsV3);

            persistS3.importFiles(IRIS_H2O_AWS, null, files, keys, fails, deletions);
            assertEquals(0, fails.size());
            assertEquals(0, deletions.size());
            assertEquals(1, files.size());
            assertEquals(1, keys.size());
        } finally {
            if(credentialsKey != null) DKV.remove(credentialsKey);
            for (String key : keys) {
                final Iced iced = DKV.getGet(key);
                assertTrue(iced instanceof Frame);
                final Frame frame = (Frame) iced;
                frame.remove();
            }
        }
    }

    @Test
    public void setS3Credentials_fail() {
        PersistS3 persistS3 = new PersistS3();
        final ArrayList<String> keys = new ArrayList<>();
        final ArrayList<String> fails = new ArrayList<>();
        final ArrayList<String> deletions = new ArrayList<>();
        final ArrayList<String> files = new ArrayList<>();
        try {
            final String nonExistingKey = UUID.randomUUID().toString();
            final PersistS3CredentialsV3 persistS3CredentialsV3 = new PersistS3CredentialsV3();
            persistS3CredentialsV3.secret_key_id = nonExistingKey;
            persistS3CredentialsV3.secret_access_key =  nonExistingKey;
            persistS3Handler.setS3Credentials(3, persistS3CredentialsV3);
            
            expectedException.expect(AmazonS3Exception.class);
            expectedException.expectMessage("The AWS Access Key Id you provided does not exist in our records. (Service: Amazon S3; Status Code: 403; Error Code: InvalidAccessKeyId;");
            persistS3.importFiles(IRIS_H2O_AWS, null, files, keys, fails, deletions);

        } finally {
            for (String key : keys) {
                final Iced iced = DKV.getGet(key);
                assertTrue(iced instanceof Frame);
                final Frame frame = (Frame) iced;
                frame.remove();
            }
        }
    }
    
    @Test
    public void setS3Credentials_nullKeyId(){
        final PersistS3CredentialsV3 persistS3CredentialsV3 = new PersistS3CredentialsV3();
        persistS3CredentialsV3.secret_key_id = null;
        persistS3CredentialsV3.secret_access_key =  "something";
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("The field 'S3_SECRET_KEY_ID' may not be null.");
        persistS3Handler.setS3Credentials(3, persistS3CredentialsV3);
    }

    @Test
    public void setS3Credentials_nullAccessKey(){
        final PersistS3CredentialsV3 persistS3CredentialsV3 = new PersistS3CredentialsV3();
        persistS3CredentialsV3.secret_key_id = "something";
        persistS3CredentialsV3.secret_access_key =  null;
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("The field 'S3_SECRET_ACCESS_KEY' may not be null.");
        persistS3Handler.setS3Credentials(3, persistS3CredentialsV3);
    }

    @Test
    public void setS3Credentials_emptyKeyId(){
        final PersistS3CredentialsV3 persistS3CredentialsV3 = new PersistS3CredentialsV3();
        persistS3CredentialsV3.secret_key_id = " "; //Space inside tests the value gets trimmed
        persistS3CredentialsV3.secret_access_key =  "something";
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("The field 'S3_SECRET_KEY_ID' may not be empty.");
        persistS3Handler.setS3Credentials(3, persistS3CredentialsV3);
    }

    @Test
    public void setS3Credentials_emptySecretAccessKey(){
        final PersistS3CredentialsV3 persistS3CredentialsV3 = new PersistS3CredentialsV3();
        persistS3CredentialsV3.secret_key_id = "something";
        persistS3CredentialsV3.secret_access_key =  " "; // Space inside tests the value gets trimmed
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("The field 'S3_SECRET_ACCESS_KEY' may not be empty.");
        persistS3Handler.setS3Credentials(3, persistS3CredentialsV3);
    }
}
