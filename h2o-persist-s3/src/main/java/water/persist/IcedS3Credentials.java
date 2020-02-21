package water.persist;

import water.Iced;

/**
 * Amazon S3 Credentials wrapper
 */
public class IcedS3Credentials extends Iced {

    public static final String S3_CREDENTIALS_DKV_KEY = "S3_CREDENTIALS_KEY";
    final String _secretKeyId;
    final String _secretAccessKey;
    final String _sessionToken;

    /**
     * @param secretKeyId     AWS Credentials secret key.
     * @param secretAccessKey AWS Credentials secret access key
     * @param sessionToken    AWS Session token - only for authorazion with session tokens - might be null.
     */
    public IcedS3Credentials(final String secretKeyId, final String secretAccessKey,
                             final String sessionToken) {
        _secretKeyId = secretKeyId;
        _secretAccessKey = secretAccessKey;
        _sessionToken = sessionToken;
    }

    public boolean isAWSSessionTokenAuth() {
        return _sessionToken != null && _secretAccessKey != null && _secretKeyId != null;
    }

    public boolean isAWSCredentialsAuth() {
        return _sessionToken == null // Session token must be set to null in order to use AWS Credentials auth
                && _secretAccessKey != null && _secretKeyId != null;
    }
}
