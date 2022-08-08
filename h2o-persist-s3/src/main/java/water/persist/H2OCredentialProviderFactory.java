package water.persist;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.alias.CredentialProvider;
import org.apache.hadoop.security.alias.CredentialProviderFactory;
import water.util.Log;

import java.net.URI;
import java.util.Collections;
import java.util.List;

public class H2OCredentialProviderFactory extends CredentialProviderFactory {

    @Override
    public CredentialProvider createProvider(URI uri, Configuration configuration) {
        if ("hex".equals(uri.getScheme()) && getClass().getName().equals(uri.getHost()))
            return new H2OCredentialProvider(new PersistS3.H2OAWSCredentialsProviderChain());
        else
            return null;
    }

    static class H2OCredentialProvider extends CredentialProvider {

        private final AWSCredentialsProvider _awsCredentialsProvider;


        public H2OCredentialProvider(AWSCredentialsProvider awsCredentialsProvider) {
            _awsCredentialsProvider = awsCredentialsProvider;
        }

        @Override
        public void flush() {
            // nothing to do
        }

        @Override
        public CredentialEntry getCredentialEntry(String s) {
            try {
                if ("fs.s3a.access.key".equals(s)) {
                    AWSCredentials credentials = _awsCredentialsProvider.getCredentials();
                    return new H2OCredentialEntry("fs.s3a.access.key", credentials.getAWSAccessKeyId().toCharArray());
                } else if ("fs.s3a.secret.key".equals(s)) {
                    AWSCredentials credentials = _awsCredentialsProvider.getCredentials();
                    return new H2OCredentialEntry("fs.s3a.secret.key", credentials.getAWSSecretKey().toCharArray());
                }
            } catch (Exception e) {
                Log.warn("Failed to retrieve '" + s + "' using the H2O built-in credentials chain.");
            }
            return null;
        }

        @Override
        public List<String> getAliases() {
            return Collections.emptyList();
        }

        @Override
        public CredentialEntry createCredentialEntry(String s, char[] chars) {
            throw new UnsupportedOperationException("AWS Credentials are read-only: unable to create new entry");
        }

        @Override
        public void deleteCredentialEntry(String s) {
            throw new UnsupportedOperationException("AWS Credentials are read-only: unable to delete an entry");
        }
    }

    static class H2OCredentialEntry extends CredentialProvider.CredentialEntry {
        protected H2OCredentialEntry(String alias, char[] credential) {
            super(alias, credential);
        }
    }

}
