package water.hive;

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

public class HiveTokenGenerator {

  private static final String HIVE_DRIVER_CLASS = "org.apache.hive.jdbc.HiveDriver";

  private static boolean isPresent(String value) {
    return value != null && !value.isEmpty();
  }

  public static String makeHivePrincipalJdbcUrl(String hiveJdbcUrlPattern, String hiveHost, String hivePrincipal) {
    if (isPresent(hiveJdbcUrlPattern) && isPresent(hivePrincipal)) {
      String result = hiveJdbcUrlPattern;
      if (hiveHost != null) result = result.replace("{{host}}", hiveHost);
      result = result.replace("{{auth}}", "principal=" + hivePrincipal);
      return result;
    } else if (isPresent(hiveHost) && isPresent(hivePrincipal)) {
      return "jdbc:hive2://" + hiveHost + "/" + ";principal=" + hivePrincipal;
    } else {
      return null;
    }
  }

  public static String makeHiveDelegationTokenJdbcUrl(String hiveJdbcUrlPattern, String hiveHost) {
    if (isPresent(hiveJdbcUrlPattern)) {
      String result = hiveJdbcUrlPattern;
      if (hiveHost != null) result = result.replace("{{host}}", hiveHost);
      result = result.replace("{{auth}}", "auth=delegationToken");
      return result;
    } else if (isPresent(hiveHost)) {
      return "jdbc:hive2://" + hiveHost + "/" + ";auth=delegationToken";
    } else
      return null;
  }
  
  public static String getHiveDelegationTokenIfHivePresent(
      String hiveJdbcUrlPattern, String hiveHost, String hivePrincipal
  ) throws IOException, InterruptedException {
    if (isHiveDriverPresent()) {
      final String hiveJdbcUrl = makeHivePrincipalJdbcUrl(hiveJdbcUrlPattern, hiveHost, hivePrincipal);
      return new HiveTokenGenerator().getHiveDelegationToken(hiveJdbcUrl, hivePrincipal);
    } else {
      log("Hive driver not present, not generating token.", null);
      return null;
    }
  }
  
  public static boolean addHiveDelegationTokenIfHivePresent(
      Job job, String hiveJdbcUrlPattern, String hiveHost, String hivePrincipal
  ) throws IOException, InterruptedException {
    if (isHiveDriverPresent()) {
      final String hiveJdbcUrl = makeHivePrincipalJdbcUrl(hiveJdbcUrlPattern, hiveHost, hivePrincipal);
      return new HiveTokenGenerator().addHiveDelegationToken(job, hiveJdbcUrl, hivePrincipal);
    } else {
      log("Hive driver not present, not generating token.", null);
      return false;
    }
  }

  public boolean addHiveDelegationToken(
      Job job,
      String hiveJdbcUrl,
      String hivePrincipal
  ) throws IOException, InterruptedException {
    if (!isPresent(hiveJdbcUrl) || !isPresent(hivePrincipal)) {
      log("Hive JDBC URL or principal not set, no token generated.", null);
      return false;
    }
    String token = getHiveDelegationToken(hiveJdbcUrl, hivePrincipal);
    if (token != null) {
      DelegationTokenPrinter.printToken(token);
      addHiveDelegationToken(job, token);
      return true;
    } else {
      log("Failed to get delegation token.", null);
      return false;
    }
  }

  public static void addHiveDelegationToken(Job job, String token) throws IOException {
    Credentials creds = tokenToCredentials(token);
    job.getCredentials().addAll(creds);
  }

  private String getHiveDelegationToken(String hiveJdbcUrl, String hivePrincipal) throws IOException, InterruptedException {
    UserGroupInformation currentUser = UserGroupInformation.getCurrentUser();
    UserGroupInformation realUser = currentUser;
    if (realUser.getRealUser() != null) {
      realUser = realUser.getRealUser();
    }
    return getHiveDelegationTokenAsUser(realUser, currentUser, hiveJdbcUrl, hivePrincipal);
  }

  public String getHiveDelegationTokenAsUser(
      UserGroupInformation realUser, final UserGroupInformation user, final String hiveJdbcUrl, final String hivePrincipal
  ) throws IOException, InterruptedException {
    return realUser.doAs(new PrivilegedExceptionAction<String>() {
      @Override
      public String run() {
        return getHiveDelegationTokenIfPossible(user, hiveJdbcUrl, hivePrincipal);
      }
    });
  }

  private static void log(String s, Exception e) {
    System.out.println(s);
    if (e != null) {
      e.printStackTrace(System.out);
    }
  }

  private String getDelegationTokenFromConnection(String hiveJdbcUrl, String hivePrincipal, String userName) {
    if (!isHiveDriverPresent()) {
      throw new IllegalStateException("Hive Driver not found");
    }
    try (Connection connection = DriverManager.getConnection(hiveJdbcUrl)) {
      return ((HiveConnection) connection).getDelegationToken(userName, hivePrincipal);
    } catch (SQLException e) {
      log("Failed to get connection.", e);
      return null;
    }
  }

  public String getHiveDelegationTokenIfPossible(UserGroupInformation tokenUser, String hiveJdbcUrl, String hivePrincipal) {
    if (!isHiveDriverPresent()) {
      return null;
    }
    String tokenUserName = tokenUser.getShortUserName();
    log("Getting delegation token from " + hiveJdbcUrl + ", " + tokenUserName, null);
    return getDelegationTokenFromConnection(hiveJdbcUrl, hivePrincipal, tokenUserName);
  }
  
  public static Credentials tokenToCredentials(String tokenStr) throws IOException {
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
