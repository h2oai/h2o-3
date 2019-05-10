package water.persist;

import water.DKV;
import water.Key;
import water.MRTask;
import water.api.Handler;

import java.util.Objects;

public class PersistS3AHandler extends Handler {

  public PersistS3ACredentialsV3 setS3ACredentials(final int version, final PersistS3ACredentialsV3 credentials) {
    validateS3Credentials(credentials);

    IcedS3ACredentials icedCredentials = new IcedS3ACredentials(credentials.access_key_id, credentials.secret_access_key);
    DKV.put(Key.make(IcedS3ACredentials.S3A_CREDENTIALS_DKV_KEY), icedCredentials);

    return credentials;
  }

  /**
   * Checks for basic mistakes users might make when providing S3 credentials.
   *
   * @param s3aCredentials S3A credentials provided by the user
   */
  private void validateS3Credentials(final PersistS3ACredentialsV3 s3aCredentials) {
    Objects.requireNonNull(s3aCredentials);

    if (s3aCredentials.access_key_id == null)
      throw new IllegalArgumentException("Value for 'fs.s3.awsAccessKeyId' may not be null.");
    if (s3aCredentials.secret_access_key == null)
      throw new IllegalArgumentException("Value for 'fs.s3.awsSecretAccessKey' may not be null.");

    s3aCredentials.access_key_id = s3aCredentials.access_key_id.trim();
    s3aCredentials.secret_access_key = s3aCredentials.secret_access_key.trim();

    if (s3aCredentials.access_key_id.isEmpty())
      throw new IllegalArgumentException("Value for 'awsAccessKeyId' may not be empty.");
    if (s3aCredentials.secret_access_key.isEmpty())
      throw new IllegalArgumentException("Value for 'fs.s3.awsSecretAccessKey' may not be empty.");

  }

}
