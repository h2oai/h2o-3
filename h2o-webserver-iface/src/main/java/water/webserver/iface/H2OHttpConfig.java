package water.webserver.iface;

/**
 * Holds configuration relevant to HTTP server.
 */
public class H2OHttpConfig {

  /**
   * Prefix of hidden system properties, same as in H2O.OptArgs.SYSTEM_PROP_PREFIX.
   */
  public static final String SYSTEM_PROP_PREFIX = "sys.ai.h2o.";

  public String jks;

  public String jks_pass;

  public LoginType loginType;

  public String login_conf;

  public String spnego_properties;

  public boolean form_auth;

  public int session_timeout; // parsed value (in minutes)

  public String user_name;

  public String context_path;

}
