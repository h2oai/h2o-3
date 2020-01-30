package water.hadoop;

import org.apache.hadoop.conf.Configuration;
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

  public static class HiveOptions {
    
    final String _jdbcUrl;
    final String _principal;

    public HiveOptions(String hiveJdbcUrl, String hivePrincipal) {
      _jdbcUrl = hiveJdbcUrl;
      _principal = hivePrincipal;
    }

    public static HiveOptions make(String hiveJdbcUrl, String hivePrincipal) {
      if (isPresent(hiveJdbcUrl) && isPresent(hivePrincipal)) {
        return new HiveOptions(hiveJdbcUrl, hivePrincipal);
      } else {
        return null;
      }
    }
    
    public static HiveOptions make(Configuration conf) {
      String hiveJdbcUrl = conf.get(H2O_HIVE_JDBC_URL);
      String hivePrincipal = conf.get(H2O_HIVE_PRINCIPAL);
      return make(hiveJdbcUrl, hivePrincipal);
    }
    
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isEmpty();
  }

  static String makeHiveJdbcUrl(String hiveJdbcUrlPattern, String hiveHost, String hivePrincipal) {
    if (hiveJdbcUrlPattern != null) {
      String result = hiveJdbcUrlPattern;
      if (hiveHost != null)
        result = result.replace("{{host}}", hiveHost);
      if (hivePrincipal != null)
        result = result.replace("{{principal}}", hivePrincipal);
      return result;
    } else if (isPresent(hiveHost) && isPresent(hivePrincipal)) {
      return "jdbc:hive2://" + hiveHost + "/" + ";principal=" + hivePrincipal;
    } else
      return null;
  }
  
  public static void addHiveDelegationTokenIfHivePresent(
      Job job, String hiveJdbcUrlPattern, String hiveHost, String hivePrincipal
  ) throws IOException, InterruptedException {
    final String hiveJdbcUrl = makeHiveJdbcUrl(hiveJdbcUrlPattern, hiveHost, hivePrincipal); 
    if (isHiveDriverPresent()) {
      new HiveTokenGenerator().addHiveDelegationToken(job, hiveJdbcUrl, hivePrincipal);
    } else {
      log("Hive driver not present, not generating token.", null);
      Configuration conf = job.getConfiguration();
      // pass configured values if any to mapper
      if (hiveJdbcUrl != null) conf.set(H2O_HIVE_JDBC_URL, hiveJdbcUrl);
      if (hivePrincipal != null) conf.set(H2O_HIVE_PRINCIPAL, hivePrincipal);
    }
  }

  public void addHiveDelegationToken(
      Job job,
      String hiveJdbcUrl,
      String hivePrincipal
  ) throws IOException, InterruptedException {
    HiveOptions options = HiveOptions.make(hiveJdbcUrl, hivePrincipal);
    if (options == null) {
      log("Hive JDBC URL or principal not set, no token generated.", null);
      return;
    }
    Configuration conf = job.getConfiguration();
    conf.set(H2O_HIVE_JDBC_URL, options._jdbcUrl);
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
    if (!isHiveDriverPresent()) {
      throw new IllegalStateException("Hive Driver not found");
    }
    try (Connection connection = DriverManager.getConnection(url)) {
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
    log("Getting delegation token from " + options._jdbcUrl + ", " + currentUser, null);

    String tokenStr = getDelegationTokenFromConnection(options._jdbcUrl, options._principal, currentUser);
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

  public static boolean isHiveDriverPresent() {
    try {
      Class.forName(HIVE_DRIVER_CLASS);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

}
