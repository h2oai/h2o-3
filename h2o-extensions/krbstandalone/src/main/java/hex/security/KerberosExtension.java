package hex.security;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import water.AbstractH2OExtension;
import water.persist.PersistHdfs;
import water.util.Log;

import java.io.IOException;

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