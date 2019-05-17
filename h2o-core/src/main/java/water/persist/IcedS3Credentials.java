package water.persist;

import water.Iced;

/**
 * Amazon S3 Credentials wrapper
 */
public class IcedS3Credentials extends Iced {
    
    public static final String S3_CREDENTIALS_DKV_KEY = "S3_CREDENTIALS_KEY";
    final String _secretKeyId;
    final String _secretAccessKey;

    public IcedS3Credentials(String secretKeyId, String secretAccessKey) {
        this._secretKeyId = secretKeyId;
        this._secretAccessKey = secretAccessKey;
    }
}
