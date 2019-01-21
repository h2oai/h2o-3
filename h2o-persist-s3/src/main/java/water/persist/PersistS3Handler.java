package water.persist;

import water.api.Handler;

import java.util.Objects;

public class PersistS3Handler extends Handler {
    
    public PersistS3CredentialsV3 setS3Credentials(final int version, final PersistS3CredentialsV3 s3Credentials){
        validateS3Credentials(s3Credentials);
        PersistS3.changeClientCredentials(s3Credentials.secret_key_id, s3Credentials.secret_access_key);
        return s3Credentials;
    }

    /**
     * Checks for basic mistakes users might make when providing S3 credentials.
     * @param s3Credentials S3 credentials provided by the user
     */
    private void validateS3Credentials(final PersistS3CredentialsV3 s3Credentials){
        Objects.requireNonNull(s3Credentials);
        Objects.requireNonNull(s3Credentials.secret_key_id);
        Objects.requireNonNull(s3Credentials.secret_access_key);

        s3Credentials.secret_key_id = s3Credentials.secret_key_id.trim();
        s3Credentials.secret_access_key = s3Credentials.secret_access_key.trim();

        if(s3Credentials.secret_key_id.isEmpty()) throw new IllegalArgumentException("The field 'SECRET_KEY_ID' may not be empty.");
        if(s3Credentials.secret_access_key.isEmpty()) throw new IllegalArgumentException("The field 'SECRET_ACCESS_KEY' may not be empty.");
        
    }
    
}
