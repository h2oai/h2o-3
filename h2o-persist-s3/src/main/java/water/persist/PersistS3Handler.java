package water.persist;

import water.DKV;
import water.Key;
import water.api.Handler;

import java.util.Objects;

public class PersistS3Handler extends Handler {
    
    public PersistS3CredentialsV3 setS3Credentials(final int version, final PersistS3CredentialsV3 s3Credentials){
        validateS3Credentials(s3Credentials);

        final IcedS3Credentials icedS3Credentials = new IcedS3Credentials(s3Credentials.secret_key_id, s3Credentials.secret_access_key,
                s3Credentials.session_token);
        DKV.put(Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY), icedS3Credentials);
        return s3Credentials;
    }

    public PersistS3CredentialsV3 removeS3Credentials(final int version, final PersistS3CredentialsV3 s3Credentials){
        DKV.remove(Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY));
        return s3Credentials;
    }

    /**
     * Checks for basic mistakes users might make when providing S3 credentials.
     * @param s3Credentials S3 credentials provided by the user
     */
    private void validateS3Credentials(final PersistS3CredentialsV3 s3Credentials){
        Objects.requireNonNull(s3Credentials);
        
        if(s3Credentials.secret_key_id == null) throw new IllegalArgumentException("The field 'S3_SECRET_KEY_ID' may not be null.");
        if(s3Credentials.secret_access_key == null) throw new IllegalArgumentException("The field 'S3_SECRET_ACCESS_KEY' may not be null.");

        s3Credentials.secret_key_id = s3Credentials.secret_key_id.trim();
        s3Credentials.secret_access_key = s3Credentials.secret_access_key.trim();
        if(s3Credentials.session_token != null) {
            s3Credentials.session_token = s3Credentials.session_token.trim();
        }

        if(s3Credentials.secret_key_id.isEmpty()) throw new IllegalArgumentException("The field 'S3_SECRET_KEY_ID' may not be empty.");
        if (s3Credentials.secret_access_key.isEmpty())
            throw new IllegalArgumentException("The field 'S3_SECRET_ACCESS_KEY' may not be empty.");
        if (s3Credentials.session_token != null && s3Credentials.session_token.isEmpty())
            throw new IllegalArgumentException("The field 'S3_SESSION_TOKEN' may not be empty");
        
    }
    
}
