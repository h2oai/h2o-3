package water.persist;

import water.Iced;

/**
 * Amazon S3 Credentials wrapper
 */
public class IcedS3ACredentials extends Iced {
    
    public static final String S3A_CREDENTIALS_DKV_KEY = "S3A_CREDENTIALS_KEY";
    String _accessKeyId;
    String _secretAccessKey;

    public IcedS3ACredentials(String accessKeyId, String secretAccessKey) {
        this._accessKeyId = accessKeyId;
        this._secretAccessKey = secretAccessKey;
    }
}
