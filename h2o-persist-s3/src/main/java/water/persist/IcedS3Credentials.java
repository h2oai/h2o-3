package water.persist;

import com.amazonaws.auth.AWSCredentials;
import water.Iced;

/**
 * Amazon S3 Credentials wrapper
 */
public class IcedS3Credentials extends Iced<IcedS3Credentials> {

    public static final String S3_CREDENTIALS_DKV_KEY = "S3_CREDENTIALS_KEY";
    final String _accessKeyId;
    final String _secretAccessKey;
    final String _sessionToken;

    /**
     * @param accessKeyId     AWS Credentials access key id.
     * @param secretAccessKey AWS Credentials secret access key.
     * @param sessionToken    AWS Session token - only for authorization with session tokens - might be null.
     */
    public IcedS3Credentials(final String accessKeyId, final String secretAccessKey,
                             final String sessionToken) {
        _accessKeyId = accessKeyId;
        _secretAccessKey = secretAccessKey;
        _sessionToken = sessionToken;
    }

    public IcedS3Credentials(final AWSCredentials credentials) {
        this(credentials.getAWSAccessKeyId(), credentials.getAWSSecretKey(), null);
    }
    
    public boolean isAWSSessionTokenAuth() {
        return _sessionToken != null && _secretAccessKey != null && _accessKeyId != null;
    }

    public boolean isAWSCredentialsAuth() {
        return _sessionToken == null // Session token must be set to null in order to use AWS Credentials auth
                && _secretAccessKey != null && _accessKeyId != null;
    }
}
