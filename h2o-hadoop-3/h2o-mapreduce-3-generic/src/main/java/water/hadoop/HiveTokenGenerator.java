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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class HiveTokenGenerator {
  
  private static final String HIVE_DRIVER_CLASS = "org.apache.hive.jdbc.HiveDriver";
  
  private static final String HIVE_PRINCIPAL_CONF = "hive.metastore.kerberos.principal";
  
  private static final String HIVE_URI_CONF = "hive.metastore.uris";
  
  private final boolean debug;

  public HiveTokenGenerator(boolean debug) {
    this.debug = debug;
  }

  public void addHiveDelegationToken(Job job, String hiveHost, String hivePrincipal) throws IOException {
    addHiveDelegationTokenIfPossible(
        job.getConfiguration(),
        hiveHost, hivePrincipal,
        job.getCredentials()
    );
  }
  
  private void DBG(String s, Exception e) {
    if (!debug) return;
    System.err.println(s);
    if (e != null) {
      e.printStackTrace();
    }
  }

  private String getDelegationTokenFromConnection(String url, String principal, String userName) {
    try (Connection connection = DriverManager.getConnection(url + ";principal=" + principal)) {
      return ((HiveConnection) connection).getDelegationToken(userName, principal);
    } catch (SQLException e) {
      DBG("Failed to get connection.", e);
      return null;
    }
  }
  
  private void addHiveDelegationTokenIfPossible(Configuration conf, String hiveHost, String hivePrincipal, Credentials creds) throws IOException {
    if (!isHiveDriverPresent()) {
      DBG("Hive driver not present, not generating token.", null);
      return;
    }

    HiveConf hiveConf = new HiveConf(conf, HiveConf.class);
    if (hiveHost == null) {
      hiveHost = hiveConf.getTrimmed(HIVE_URI_CONF, "");
    }
    if (hivePrincipal == null) {
      hivePrincipal = hiveConf.getTrimmed(HIVE_PRINCIPAL_CONF, "");
    }

    if (hivePrincipal.isEmpty() || hiveHost.isEmpty()) {
      DBG("Hive host and principal missing, no token generated.", null);
      return;
    }
    
    String currentUser = UserGroupInformation.getCurrentUser().getShortUserName();
    String hiveJdbcUrl = "jdbc:hive2://" + hiveHost + "/";
    DBG("Getting delegation token from " + hiveJdbcUrl + " with " + hivePrincipal + ", " + currentUser, null);
    
    String tokenStr = getDelegationTokenFromConnection(hiveJdbcUrl, hivePrincipal, currentUser);
    if (tokenStr != null) {
      Token<DelegationTokenIdentifier> hive2Token = new Token<>();
      hive2Token.decodeFromUrlString(tokenStr);
      hive2Token.setService(new Text("hiveserver2ClientToken"));

      creds.addToken(new Text("hive.server2.delegation.token"), hive2Token);
      creds.addToken(new Text("hiveserver2ClientToken"), hive2Token); //HiveAuthConstants.HS2_CLIENT_TOKEN
    }
  }
  
  private boolean isHiveDriverPresent() {
    try {
      Class.forName(HIVE_DRIVER_CLASS);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

}
