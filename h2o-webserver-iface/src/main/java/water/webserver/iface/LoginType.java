package water.webserver.iface;

/**
 * Supported login methods
 */
public enum LoginType {
  NONE(null, false),
  HASH(null, false),
  LDAP("ldaploginmodule", true),
  KERBEROS("krb5loginmodule", true),
  SPNEGO(null, true),
  PAM("pamloginmodule", true);

  public final String jaasRealm;
  private final boolean checkUserName;

  LoginType(final String jaasRealm, boolean checkUserName) {
    this.jaasRealm = jaasRealm;
    this.checkUserName = checkUserName;
  }

  public boolean needToCheckUserName() {
    return checkUserName;
  }

}
