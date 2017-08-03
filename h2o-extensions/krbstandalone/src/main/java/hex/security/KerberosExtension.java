package hex.security;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import water.AbstractH2OExtension;
import water.persist.PersistHdfs;
import water.util.Log;

import java.io.IOException;

/**
 * Authenticates the H2O Node to access secured Hadoop cluster in a standalone mode.
 *
 * This extension assumes that if Hadoop configuration is present and it has Kerberos enabled
 * the user will likely want to read data from HDFS even though H2O is running in a standalone mode (not on Hadoop).
 * The extension attempts to authenticate the user using an existing Kerberos ticket. This means the Kerberos ticket
 * needs to be manually acquired by the user on each node before the H2O instance is started.
 *
 * The extension fails gracefully if the user cannot be authenticated and doesn't stop H2O start-up. The failure
 * will be logged as an error.
 */
public class KerberosExtension extends AbstractH2OExtension {

  public static String NAME = "KrbStandalone";

  @Override
  public String getExtensionName() {
    return NAME;
  }

  @Override
  public void onLocalNodeStarted() {
    Configuration conf = PersistHdfs.CONF;
    if (conf == null)
      return; // this is theoretically possible although unlikely

    if (isKerberosEnabled(conf)) {
      Log.info("Kerberos enabled in Hadoop configuration. Trying to login the (default) user.");
      UserGroupInformation.setConfiguration(conf);
      try {
        UserGroupInformation.loginUserFromSubject(null);
      } catch (IOException e) {
        Log.err("Kerberos initialization FAILED. Kerberos ticket needs to be acquired before starting H2O (run kinit).", e);
      }
    } else
      Log.debug("Kerberos not configured");
  }

  private static boolean isKerberosEnabled(Configuration conf) {
    return "kerberos".equals(conf.get("hadoop.security.authentication"));
  }

}