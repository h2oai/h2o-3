package water;

import water.util.Log;

/**
 * H2O-3 Enterprise messaging for the open-source build.
 *
 * Single place for the OSS-vs-Enterprise wording so the server banner, the Python client and
 * the R client stay consistent. This class only carries the message; it does not restrict
 * anything.
 */
public class EnterpriseGate {

  public static final String ENTERPRISE_EMAIL = "enterprise@h2o.ai";
  public static final String LEARN_MORE = "h2o.ai/h2o-3/oss-vs-enterprise";

  private EnterpriseGate() {}

  /**
   * Log the OSS-vs-Enterprise notice at cluster startup. Emitted via {@link Log#info}
   * (not System.out) so it flows through the standard logging pipeline.
   */
  public static void logStartupBanner() {
    Log.info("You are running the community edition of H2O-3 OSS.");
    Log.info("For commercial use, H2O-3 Enterprise is now recommended.");
    Log.info("This includes production support, CVE fixes, multi-node scaling, " +
             "model artifact extraction, and more.");
    Log.info("See " + LEARN_MORE + " for additional details.");
    Log.info("Contact " + ENTERPRISE_EMAIL + " to upgrade.");
  }
}
