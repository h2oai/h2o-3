package water.hive;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import water.H2O;
import water.MRTask;
import water.Paxos;
import water.util.BinaryFileTransfer;
import water.util.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DelegationTokenRefresher implements Runnable {

  public static final String H2O_AUTH_USER = "h2o.auth.user";
  public static final String H2O_AUTH_PRINCIPAL = "h2o.auth.principal";
  public static final String H2O_AUTH_KEYTAB = "h2o.auth.keytab";
  public static final String H2O_HIVE_USE_KEYTAB = "h2o.hive.useKeytab";
  public static final String H2O_HIVE_JDBC_URL_PATTERN = "h2o.hive.jdbc.urlPattern";
  public static final String H2O_HIVE_HOST = "h2o.hive.jdbc.host";
  public static final String H2O_HIVE_PRINCIPAL = "h2o.hive.principal";
  public static final String H2O_HIVE_TOKEN = "h2o.hive.token";

  public static void setup(Configuration conf, String tmpDir) throws IOException {
    if (!HiveTokenGenerator.isHiveDriverPresent()) {
      return;
    }
    String token = conf.get(H2O_HIVE_TOKEN);
    if (token != null) {
      log("Adding credentials from property", null);
      Credentials creds = HiveTokenGenerator.tokenToCredentials(token);
      UserGroupInformation.getCurrentUser().addCredentials(creds);
    }
    String authUser = conf.get(H2O_AUTH_USER);
    String authPrincipal = conf.get(H2O_AUTH_PRINCIPAL);
    boolean useKeytab = conf.getBoolean(H2O_HIVE_USE_KEYTAB, true);
    String authKeytab = useKeytab ? conf.get(H2O_AUTH_KEYTAB) : null;
    String hiveJdbcUrlPattern = conf.get(H2O_HIVE_JDBC_URL_PATTERN);
    String hiveHost = conf.get(H2O_HIVE_HOST);
    String hivePrincipal = conf.get(H2O_HIVE_PRINCIPAL);
    final String hiveJdbcUrl;
    if (authKeytab != null) {
      hiveJdbcUrl = HiveTokenGenerator.makeHivePrincipalJdbcUrl(hiveJdbcUrlPattern, hiveHost, hivePrincipal);
    } else {
      hiveJdbcUrl = HiveTokenGenerator.makeHiveDelegationTokenJdbcUrl(hiveJdbcUrlPattern, hiveHost);
    }
    if (hiveJdbcUrl != null) {
      String authKeytabPath;
      if (authKeytab != null) {
        authKeytabPath = writeKeytabToFile(authKeytab, tmpDir);
      } else {
        authKeytabPath = null;
      }
      new DelegationTokenRefresher(authPrincipal, authKeytabPath, authUser, hiveJdbcUrl, hivePrincipal).start();
    } else {
      log("Delegation token refresh not active.", null);
    }
  }
  
  private static String writeKeytabToFile(String authKeytab, String tmpDir) throws IOException {
    FileUtils.makeSureDirExists(tmpDir);
    String fileName = tmpDir + File.separator + "auth_keytab";
    byte[] byteArr = BinaryFileTransfer.convertStringToByteArr(authKeytab);
    BinaryFileTransfer.writeBinaryFile(fileName, byteArr);
    return fileName;
  }

  private final ScheduledExecutorService _executor = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setDaemon(true).setNameFormat("delegation-token-refresher-%d").build()
  );

  private final String _authPrincipal;
  private final String _authKeytabPath;
  private final String _authUser;
  private final String _hiveJdbcUrl;
  private final String _hivePrincipal;

  private final HiveTokenGenerator _hiveTokenGenerator = new HiveTokenGenerator();

  public DelegationTokenRefresher(
      String authPrincipal,
      String authKeytabPath,
      String authUser,
      String hiveJdbcUrl,
      String hivePrincipal
  ) {
    this._authPrincipal = authPrincipal;
    this._authKeytabPath = authKeytabPath;
    this._authUser = authUser;
    this._hiveJdbcUrl = hiveJdbcUrl;
    this._hivePrincipal = hivePrincipal;
  }

  public void start() {
    _executor.scheduleAtFixedRate(this, 0, 1, TimeUnit.MINUTES);
  }
  
  private static void log(String s, Exception e) {
    System.out.println("TOKEN REFRESH: " + s);
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
    try {
      refreshTokens();
    } catch (IOException | InterruptedException e) {
      log("Failed to refresh token.", e);
    }
  }
  
  private static class DistributeCreds extends MRTask<DistributeCreds> {
    
    private final byte[] _credsSerialized;

    private DistributeCreds(byte[] credsSerialized) {
      this._credsSerialized = credsSerialized;
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
  }
  
  private void distribute(Credentials creds) throws IOException {
    if (!Paxos._cloudLocked) {
      // skip token distribution in pre-cloud forming phase, only use credentials locally
      log("Updating credentials", null);
      UserGroupInformation.getCurrentUser().addCredentials(creds);
    } else {
      byte[] credsSerialized = serializeCreds(creds);
      new DistributeCreds(credsSerialized).doAllNodes();
    }
  }

  private void refreshTokens() throws IOException, InterruptedException {
    String token;
    if (_authKeytabPath != null) {
      log("Log in from keytab as " + _authPrincipal, null);
      UserGroupInformation realUser = UserGroupInformation.loginUserFromKeytabAndReturnUGI(_authPrincipal, _authKeytabPath);
      UserGroupInformation tokenUser = realUser;
      if (_authUser != null) {
        log("Impersonate " + _authUser, null);
        // attempt to impersonate token user, this verifies that the real-user is able to impersonate tokenUser
        tokenUser = UserGroupInformation.createProxyUser(_authUser, tokenUser);
      }
      token = _hiveTokenGenerator.getHiveDelegationTokenAsUser(realUser, tokenUser, _hiveJdbcUrl, _hivePrincipal);
    } else {
      UserGroupInformation currentUser = UserGroupInformation.getCurrentUser();
      token = _hiveTokenGenerator.getHiveDelegationTokenIfPossible(currentUser, _hiveJdbcUrl, _hivePrincipal);
    }
    if (token != null) {
      DelegationTokenPrinter.printToken(token);
      Credentials creds = HiveTokenGenerator.tokenToCredentials(token);
      distribute(creds);
    } else {
      log("Failed to refresh delegation token.", null);
    }
  }

  private byte[] serializeCreds(Credentials creds) throws IOException {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    DataOutputStream dataStream = new DataOutputStream(byteStream);
    creds.writeTokenStorageToStream(dataStream);
    return byteStream.toByteArray();
  }

}
