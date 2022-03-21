package water.persist;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import water.H2O;
import water.Paxos;
import water.persist.security.HdfsDelegationTokenRefresher;
import water.util.Log;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class S3ATokenRefresherFactory {

    private static final String H2O_DYNAMIC_AUTH_S3A_TOKEN_REFRESHER_ENABLED = "h2o.auth.dynamicS3ATokenRefresher.enabled";

    private final Configuration conf;
    private final String tmpDir;
    
    private final Set<String> bucketsWithDelegationToken = Collections.synchronizedSet(new HashSet<>());
    private final Object GENERATION_LOCK = new Object();

    S3ATokenRefresherFactory(Configuration conf, String tmpDir) {
        this.conf = conf;
        this.tmpDir = tmpDir;
    }

    /**
     * Starts a delegation token refresher if given path is compatible with this refresher.
     * 
     * @param p path
     * @return flag indicating whether the path was handled or if we need to try another refresher to handle this path
     * @throws IOException
     */
    boolean startDelegationTokenRefresher(Path p) throws IOException {
        if (Paxos._cloudLocked && H2O.CLOUD.leader() != H2O.SELF) {
            // fast path - cloud already locked, and I am not the leader, give up - only the cloud leader is allowed to refresh the tokens
            return false; // not handled (we didn't touch the path even)
        }

        final URI uri = p.toUri();
        if (!"s3a".equalsIgnoreCase(uri.getScheme())) {
            // only S3A needs to generate delegation token
            if (Log.isLoggingFor(Log.DEBUG)) {
                Log.debug("Delegation token refresh is only needed for s3a, requested URI: " + uri);
            }
            return false; // not handled, different from s3a
        }

        // Important make sure the cloud is locked in order to guarantee that the leader will distribute credentials
        // to all nodes and don't do refresh only for itself (which can happen if cloud is not yet locked)
        Paxos.lockCloud("S3A Token Refresh");

        if (H2O.CLOUD.leader() != H2O.SELF) {
            // we are not a leader node in a locked cloud, give up
            return true; // handled (by the leader - assumed, not checked)
        }

        synchronized (GENERATION_LOCK) {
            if (isInBucketWithAlreadyExistingToken(uri)) {
                return true;
            }
            final String bucketIdentifier = p.toUri().getHost();
            HdfsDelegationTokenRefresher.setup(conf, tmpDir, p.toString());
            Log.debug("Bucket added to bucketsWithDelegationToken: '" + bucketIdentifier + "'");
            bucketsWithDelegationToken.add(bucketIdentifier);
        }
        return true; // handled by us
    }

    private boolean isInBucketWithAlreadyExistingToken(URI uri) {
        return bucketsWithDelegationToken.contains(uri.getHost());
    }

    public static S3ATokenRefresherFactory make(Configuration conf, String tmpDir) {
        if (conf == null ||
                !conf.getBoolean(H2O_DYNAMIC_AUTH_S3A_TOKEN_REFRESHER_ENABLED, false)) {
            return null;
        }
        return new S3ATokenRefresherFactory(conf, tmpDir);
    }

}
