package water.persist.security;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.security.token.delegation.AbstractDelegationTokenIdentifier;
import water.H2O;
import water.MRTask;
import water.Paxos;
import water.persist.PersistHdfs;
import water.util.BinaryFileTransfer;
import water.util.FileUtils;

import java.io.*;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HdfsDelegationTokenRefresher implements Runnable {

    public static final String H2O_AUTH_USER = "h2o.auth.user";
    public static final String H2O_AUTH_PRINCIPAL = "h2o.auth.principal";
    public static final String H2O_AUTH_KEYTAB = "h2o.auth.keytab";
    public static final String H2O_AUTH_TOKEN_REFRESHER_ENABLED = "h2o.auth.tokenRefresher.enabled";
    public static final String H2O_AUTH_TOKEN_REFRESHER_INTERVAL_RATIO = "h2o.auth.tokenRefresher.intervalRatio";
    public static final String H2O_AUTH_TOKEN_REFRESHER_MAX_ATTEMPTS = "h2o.auth.tokenRefresher.maxAttempts";
    public static final String H2O_AUTH_TOKEN_REFRESHER_RETRY_DELAY_SECS = "h2o.auth.tokenRefresher.retryDelaySecs";
    public static final String H2O_AUTH_TOKEN_REFRESHER_FALLBACK_INTERVAL_SECS = "h2o.auth.tokenRefresher.fallbackIntervalSecs";

    public static void setup(Configuration conf, String tmpDir) throws IOException {
        boolean enabled = conf.getBoolean(H2O_AUTH_TOKEN_REFRESHER_ENABLED, false);
        if (!enabled) {
            log("HDFS Token renewal is not enabled in configuration", null);
            return;
        }
        String authUser = conf.get(H2O_AUTH_USER);
        String authPrincipal = conf.get(H2O_AUTH_PRINCIPAL);
        if (authPrincipal == null) {
            log("Principal not provided, HDFS tokens will not be refreshed by H2O and their lifespan will be limited", null);
        }
        String authKeytab = conf.get(H2O_AUTH_KEYTAB);
        if (authKeytab == null) {
            log("Keytab not provided, HDFS tokens will not be refreshed by H2O and their lifespan will be limited", null);
            return;
        }
        String authKeytabPath = writeKeytabToFile(authKeytab, tmpDir);
        new HdfsDelegationTokenRefresher(conf, authPrincipal, authKeytabPath, authUser).start();
    }

    private static String writeKeytabToFile(String authKeytab, String tmpDir) throws IOException {
        FileUtils.makeSureDirExists(tmpDir);
        File keytabFile = new File(tmpDir, "hdfs_auth_keytab");
        byte[] byteArr = BinaryFileTransfer.convertStringToByteArr(authKeytab);
        BinaryFileTransfer.writeBinaryFile(keytabFile.getAbsolutePath(), byteArr);
        return keytabFile.getAbsolutePath();
    }

    private final ScheduledExecutorService _executor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setDaemon(true).setNameFormat("hdfs-token-refresher-%d").build()
    );

    private final String _authPrincipal;
    private final String _authKeytabPath;
    private final String _authUser;
    private final double _intervalRatio;
    private final int _maxAttempts;
    private final int _retryDelaySecs;
    private final long _fallbackIntervalSecs;

    public HdfsDelegationTokenRefresher(
            Configuration conf,
            String authPrincipal,
            String authKeytabPath,
            String authUser
    ) {
        _authPrincipal = authPrincipal;
        _authKeytabPath = authKeytabPath;
        _authUser = authUser;
        _intervalRatio = Double.parseDouble(conf.get(H2O_AUTH_TOKEN_REFRESHER_INTERVAL_RATIO, "0.4"));
        _maxAttempts = conf.getInt(H2O_AUTH_TOKEN_REFRESHER_MAX_ATTEMPTS, 12);
        _retryDelaySecs = conf.getInt(H2O_AUTH_TOKEN_REFRESHER_RETRY_DELAY_SECS, 10);
        _fallbackIntervalSecs = conf.getInt(H2O_AUTH_TOKEN_REFRESHER_FALLBACK_INTERVAL_SECS, 12 * 3600); // 12h
    }

    public void start() {
        long intervalSecs = 0L;
        try {
             intervalSecs = getTokenRenewalIntervalSecs(loginAuthUser());
        } catch (IOException | InterruptedException e) {
            log("Encountered error while trying to determine token renewal interval.", e);
        }
        final long actualIntervalSecs;
        if (intervalSecs == 0L) {
            actualIntervalSecs = _fallbackIntervalSecs;
            log("Token renewal interval was not determined, will use " + _fallbackIntervalSecs + "s.", null);
        } else {
            actualIntervalSecs = (long) (intervalSecs * _intervalRatio);
            log("Determined token renewal interval = " + intervalSecs + "s. " +
                    "Using actual interval = " + actualIntervalSecs + "s (ratio=" + _intervalRatio + ").", null);
        }
        _executor.scheduleAtFixedRate(this, 0, actualIntervalSecs, TimeUnit.SECONDS);
    }

    private static void log(String s, Exception e) {
        System.out.println("HDFS TOKEN REFRESH: " + s);
        if (e != null) {
            e.printStackTrace(System.out);
        }
    }

    @Override
    public void run() {
        if (Paxos._cloudLocked && !(H2O.CLOUD.leader() == H2O.SELF)) {
            // cloud is formed the leader will take of subsequent refreshes
            _executor.shutdown();
            return;
        }
        for (int i = 0; i < _maxAttempts; i++) {
            try {
                Credentials creds = refreshTokens(loginAuthUser());
                distribute(creds);
                return;
            } catch (IOException | InterruptedException e) {
                log("Failed to refresh token (attempt " + i + " out of " + _maxAttempts + "). Will retry in " + _retryDelaySecs + "s.", e);
            }
            try {
                Thread.sleep(_retryDelaySecs * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Credentials refreshTokens(UserGroupInformation tokenUser) throws IOException, InterruptedException {
        return tokenUser.doAs((PrivilegedExceptionAction<Credentials>) () -> {
            Credentials creds = new Credentials();
            Token<?>[] tokens = fetchDelegationTokens(_authUser, creds);
            log("Fetched delegation tokens: " + Arrays.toString(tokens), null);
            return creds;
        });
    }

    private UserGroupInformation loginAuthUser() throws IOException, InterruptedException{
        log("Log in from keytab as " + _authPrincipal, null);
        UserGroupInformation realUser = UserGroupInformation.loginUserFromKeytabAndReturnUGI(_authPrincipal, _authKeytabPath);
        UserGroupInformation tokenUser = realUser;
        if (_authUser != null) {
            log("Impersonate " + _authUser, null);
            // attempt to impersonate token user, this verifies that the real-user is able to impersonate tokenUser
            tokenUser = UserGroupInformation.createProxyUser(_authUser, tokenUser);
        }
        return tokenUser;
    }
    
    private long getTokenRenewalIntervalSecs(UserGroupInformation tokenUser) throws IOException, InterruptedException {
        Credentials creds = refreshTokens(tokenUser);
        long intervalMillis = tokenUser.doAs((PrivilegedExceptionAction<Long>) () ->
            creds.getAllTokens()
                    .stream()
                    .map(token -> {
                        try {
                            long expiresAt = token.renew(PersistHdfs.CONF);
                            long issuedAt = 0;
                            TokenIdentifier ident = token.decodeIdentifier();
                            if (ident instanceof AbstractDelegationTokenIdentifier) {
                                issuedAt = ((AbstractDelegationTokenIdentifier) ident).getIssueDate();
                            }
                            return expiresAt - (issuedAt > 0 ? issuedAt : System.currentTimeMillis());
                        } catch (InterruptedException | IOException e) {
                            log("Failed to determine token expiration for token " + token, e);
                            return Long.MAX_VALUE;
                        }
                    }).min(Long::compareTo).orElse(Long.MAX_VALUE)
        );
        return intervalMillis > 0 && intervalMillis < Long.MAX_VALUE ? 
                intervalMillis / 1000 : 0L;
    }

    private static Token<?>[] fetchDelegationTokens(String renewer, Credentials credentials) throws IOException {
        return FileSystem.get(PersistHdfs.CONF).addDelegationTokens(renewer, credentials);
    } 
    
    private void distribute(Credentials creds) throws IOException {
        DistributeCreds distributeTask = new DistributeCreds(creds);
        if (!Paxos._cloudLocked) {
            // skip token distribution in pre-cloud forming phase, only use credentials locally
            distributeTask.setupLocal();
        } else {
            distributeTask.doAllNodes();
        }
    }

    private static class DistributeCreds extends MRTask<DistributeCreds> {
        private final byte[] _credsSerialized;

        private DistributeCreds(Credentials creds) throws IOException {
            _credsSerialized = serializeCreds(creds);
        }

        @Override
        protected void setupLocal() {
            try {
                Credentials creds = deserialize();
                log("Updating credentials", null);
                UserGroupInformation.getCurrentUser().addCredentials(creds);
            } catch (IOException e) {
                log("Failed to update credentials", e);
            }
        }

        private Credentials deserialize() throws IOException {
            ByteArrayInputStream tokensBuf = new ByteArrayInputStream(_credsSerialized);
            Credentials creds = new Credentials();
            creds.readTokenStorageStream(new DataInputStream(tokensBuf));
            return creds;
        }

        private static byte[] serializeCreds(Credentials creds) throws IOException {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream dataStream = new DataOutputStream(byteStream);
            creds.writeTokenStorageToStream(dataStream);
            return byteStream.toByteArray();
        }

    }

}
