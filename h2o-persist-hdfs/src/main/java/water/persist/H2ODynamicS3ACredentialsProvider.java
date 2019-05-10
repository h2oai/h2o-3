package water.persist;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import water.DKV;

public class H2ODynamicS3ACredentialsProvider implements AWSCredentialsProvider {


  @Override
  public AWSCredentials getCredentials() {
    final IcedS3ACredentials s3Credentials = DKV.getGet(IcedS3ACredentials.S3A_CREDENTIALS_DKV_KEY);

    if (s3Credentials != null && s3Credentials._secretAccessKey != null && s3Credentials._secretAccessKey != null) {
      return new BasicAWSCredentials(s3Credentials._accessKeyId, s3Credentials._secretAccessKey);
    } else {
      throw new AmazonClientException("No Amazon S3 credentials set directly.");
    }
  }

  @Override
  public void refresh() {

  }
}
