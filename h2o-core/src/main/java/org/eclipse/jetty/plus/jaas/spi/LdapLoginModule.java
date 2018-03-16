package org.eclipse.jetty.plus.jaas.spi;

/**
 * It is preferred to use org.eclipse.jetty.jaas.spi.LdapLoginModule module which is directly
 * provided by Jetty 9. This module is written just for compatibility purposes. In Jetty 8, the module
 * was named org.eclipse.jetty.plus.jaas.spi.LdapLoginModule so this just acts as the compatibility layer.
 */
public class LdapLoginModule extends org.eclipse.jetty.jaas.spi.LdapLoginModule { /* empty */ }