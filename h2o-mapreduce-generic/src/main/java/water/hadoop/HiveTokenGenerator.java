package water.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.security.token.delegation.DelegationTokenIdentifier;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hive.jdbc.HiveConnection;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static water.hadoop.h2omapper.*;

public class HiveTokenGenerator {

  private static final String HIVE_DRIVER_CLASS = "org.apache.hive.jdbc.HiveDriver";

  private static final String HIVE_PRINCIPAL_CONF = "hive.metastore.kerberos.principal";

  private static final String HIVE_URI_CONF = "hive.metastore.uris";
  
  public static class HiveOptions {
    
    final String _host;
    final String _principal;

    public HiveOptions(String hiveHost, String hivePrincipal) {
      _host = hiveHost;
      _principal = hivePrincipal;
    }
    
    public static HiveOptions make(String hiveHost, String hivePrincipal, Configuration conf) {
      HiveConf hiveConf = new HiveConf(conf, HiveConf.class);
      if (hiveHost == null) {
        hiveHost = hiveConf.getTrimmed(HIVE_URI_CONF, "");
      }
      if (hivePrincipal == null) {
        hivePrincipal = hiveConf.getTrimmed(HIVE_PRINCIPAL_CONF, "");
      }
      if (hiveHost == null || hivePrincipal == null) {
        return null;
      } else {
        return new HiveOptions(hiveHost, hivePrincipal);
      }
    }
    
    public static HiveOptions make(Configuration conf) {
      String hivePrincipal = conf.get(H2O_HIVE_PRINCIPAL);
      String hiveHost = conf.get(H2O_HIVE_HOST);
      return make(hiveHost, hivePrincipal, conf);
    }
    
  }
  
  public static void addHiveDelegationTokenIfHivePresent(
      Job job, String hiveHost, String hivePrincipal
  ) throws IOException, InterruptedException {
    if (isHiveDriverPresent()) {
      new HiveTokenGenerator().addHiveDelegationToken(job, hiveHost, hivePrincipal);
    } else {
      log("Hive driver not present, not generating token.", null);
      Configuration conf = job.getConfiguration();
      conf.set(H2O_HIVE_HOST, hiveHost);
      conf.set(H2O_HIVE_PRINCIPAL, hivePrincipal);
    }
  }

  public void addHiveDelegationToken(
      Job job,
      String hiveHost,
      String hivePrincipal
  ) throws IOException, InterruptedException {
    Configuration conf = job.getConfiguration();
    HiveOptions options = HiveOptions.make(hiveHost, hivePrincipal, conf);
    if (options == null) {
      log("Hive host or principal not set, no token generated.", null);
      return;
    }
    conf.set(H2O_HIVE_HOST, options._host);
    conf.set(H2O_HIVE_PRINCIPAL, options._principal);
    UserGroupInformation realUser = UserGroupInformation.getCurrentUser();
    if (realUser.getRealUser() != null) {
      realUser = realUser.getRealUser();
    }
    Credentials creds = addHiveDelegationTokenAsUser(realUser, options);
    if (creds != null) {
      job.getCredentials().addAll(creds);
    } else {
      log("Failed to get delegation token.", null);
    }
  }

  public Credentials addHiveDelegationTokenAsUser(
      UserGroupInformation ugi, final HiveOptions options
  ) throws IOException, InterruptedException {
    return ugi.doAs(new PrivilegedExceptionAction<Credentials>() {
      @Override
      public Credentials run() throws Exception {
        return addHiveDelegationTokenIfPossible(options);
      }
    });
  }

  private static void log(String s, Exception e) {
    System.out.println(s);
    if (e != null) {
      e.printStackTrace(System.out);
    }
  }

  private String getDelegationTokenFromConnection(String url, String principal, String userName) {
    try (Connection connection = DriverManager.getConnection(url + ";principal=" + principal)) {
      return ((HiveConnection) connection).getDelegationToken(userName, principal);
    } catch (SQLException e) {
      log("Failed to get connection.", e);
      return null;
    }
  }

  private Credentials addHiveDelegationTokenIfPossible(HiveOptions options) throws IOException {
    if (!isHiveDriverPresent()) {
      return null;
    }

    String currentUser = UserGroupInformation.getCurrentUser().getShortUserName();
    String hiveJdbcUrl = "jdbc:hive2://" + options._host + "/";
    log("Getting delegation token from " + hiveJdbcUrl + " with " + options._principal + ", " + currentUser, null);

    String tokenStr = getDelegationTokenFromConnection(hiveJdbcUrl, options._principal, currentUser);
    if (tokenStr != null) {
      Token<DelegationTokenIdentifier> hive2Token = new Token<>();
      hive2Token.decodeFromUrlString(tokenStr);
      hive2Token.setService(new Text("hiveserver2ClientToken"));

      Credentials creds = new Credentials();
      creds.addToken(new Text("hive.server2.delegation.token"), hive2Token);
      creds.addToken(new Text("hiveserver2ClientToken"), hive2Token); //HiveAuthConstants.HS2_CLIENT_TOKEN
      return creds;
    } else {
      return null;
    }
  }

  private boolean isHiveConfigPresent(String hiveHost, String hivePrincipal) {
    return hivePrincipal != null && !hivePrincipal.isEmpty() &&
        hiveHost != null && !hiveHost.isEmpty();
  }

  public static boolean isHiveDriverPresent() {
    try {
      Class.forName(HIVE_DRIVER_CLASS);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

}
