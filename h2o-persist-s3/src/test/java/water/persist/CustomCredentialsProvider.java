package water.persist;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import org.junit.Ignore;

@Ignore
public class CustomCredentialsProvider implements AWSCredentialsProvider {
    @Override
    public AWSCredentials getCredentials() {
        return new AWSCredentials() {
            @Override
            public String getAWSAccessKeyId() {
                return "testAccessKeyId";
            }

            @Override
            public String getAWSSecretKey() {
                return "testSecretKey";
            }
        };
    }

    @Override
    public void refresh() {

    }
}
