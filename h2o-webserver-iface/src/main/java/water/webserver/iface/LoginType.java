package water.webserver.iface;

/**
 * Supported login methods
 */
public enum LoginType {
  NONE(null),
  HASH(null),
  LDAP("ldaploginmodule"),
  KERBEROS("krb5loginmodule"),
  PAM("pamloginmodule");

  public final String jaasRealm;

  LoginType(final String jaasRealm) {
    this.jaasRealm = jaasRealm;
  }

  public boolean needToCheckUserName() {
    return jaasRealm != null;
  }
}
