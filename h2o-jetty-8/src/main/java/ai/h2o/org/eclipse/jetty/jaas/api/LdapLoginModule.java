package ai.h2o.org.eclipse.jetty.jaas.api;

/**
 * LdapLoginModule is relocated in Sparkling Water to the package ai.h2o.org.eclipse.jetty.jaas.spi
 * of Jetty 9. External backend workers on Hadoop 2 utilize Jetty 8 and thus the module 
 * org.eclipse.jetty.plus.jaas.spi. This class enables to use only one package name for both cases.
 */
public class LdapLoginModule extends org.eclipse.jetty.plus.jaas.spi.LdapLoginModule { /* empty */ }
