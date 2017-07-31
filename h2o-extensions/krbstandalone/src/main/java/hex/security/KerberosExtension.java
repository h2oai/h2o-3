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
    if ("kerberos".equals(conf.get("hadoop.security.authentication"))) {
      Log.info("Kerberos enabled in configuration - trying to login using existing keytab");
      UserGroupInformation.setConfiguration(conf);
      try {
        UserGroupInformation.loginUserFromSubject(null);
      } catch (IOException e) {
        throw new RuntimeException("Kerberos initialization FAILED.", e);
      }
    } else
      Log.info("Kerberos not configured");
  }

}