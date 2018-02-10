Security
========

H2O contains security features intended for deployment inside a secure
data center.

Security Model
--------------

Below is a discussion of what the security assumptions are, and what the
H2O software does and does not do.

Terms
~~~~~

+-------------------------------------+---------------------------------+
| Term                                | Definition                      |
+=====================================+=================================+
| **H2O Cluster**                     | A collection of H2O nodes that  | 
|                                     | work together. In the H2O Flow  | 
|                                     | Web UI, the cluster status menu |
|                                     | item shows the list of nodes in | 
|                                     | an H2O cluster.                 |
+-------------------------------------+---------------------------------+
| **H2O node**                        | One VM instance running the H2O |
|                                     | main class. One H2O node        | 
|                                     | corresponds to one OS-level     | 
|                                     | process. In the YARN case, one  | 
|                                     | H2O node corresponds to one     |
|                                     | mapper instance and one YARN    |
|                                     | container.                      |
+-------------------------------------+---------------------------------+
| **H2O embedded web port**           | Each Each H2O node contains an  |
|                                     | embedded web port (by default   |
|                                     | port 54321). This web port      |
|                                     | hosts H2O Flow as well as the   |
|                                     | H2O REST API. The user interacts|
|                                     | directly with this web port.    |
+-------------------------------------+---------------------------------+
| **H2O Internal communication port** | Each Each H2O node also has an  |
|                                     | internal port (web port+1, so by| 
|                                     | default port 54322) for internal| 
|                                     | node-to-node communication. This| 
|                                     | is a proprietary binary         |
|                                     | protocol. An attacker using a   |
|                                     | tool like tcpdump or wireshark  |
|                                     | may be able to reverse engineer |
|                                     | data captured on this           |
|                                     | communication path.             |
+-------------------------------------+---------------------------------+

Assumptions (Threat Model)
~~~~~~~~~~~~~~~~~~~~~~~~~~

1. H2O lives in a secure data center.

2. Denial of service is not a concern.

   -  H2O is not designed to withstand a DOS attack.

3. HTTP traffic between the user client and H2O cluster needs to be
   encrypted.

   -  This is true for both interactive sessions (e.g the H2O Flow Web
      UI) and programmatic sessions (e.g. an R program).

4. Man-in-the-middle attacks are of low concern.

   -  Certificate checking on the client side for R/python is not yet
      implemented.

5. You may want to secure internal binary H2O node-to-H2O node traffic
   via encryption.

6. You trust the person that starts H2O to start it correctly.

   -  Enabling H2O security requires specifying the correct security
      options.

7. User client sessions do not need to expire. A session lives at most
   as long as the cluster lifetime. H2O clusters are started and stopped
   "frequently enough".

   -  All data is stored in-memory, so restarting the H2O cluster wipes
      all data from memory, and there is nothing to clean from disk.

8. Once a user is authenticated for access to H2O, they have full
   access.

   -  H2O supports authentication but not authorization or access
      control (ACLs).

9. H2O clusters are meant to be accessed by only one user.

   -  Each user starts their own H2O cluster.
   -  H2O only allows access to the embedded web port to the person that
      started the cluster.

Data Chain-of-Custody in a Hadoop Data Center Environment
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

**Note**: This holds true for all versions of Hadoop (including YARN) supported by H2O.

Through this sequence, it is shown that a user is only able to access
the same data from H2O that they could already access from normal Hadoop
jobs.

1. Data lives in HDFS
2. The files in HDFS have permissions
3. An HDFS user has permissions (capabilities) to access certain files
4. Kerberos (kinit) can be used to authenticate a user in a Hadoop
   environment
5. A user's Hadoop MapReduce job inherits the permissions (capabilities)
   of the user, as well as kinit metadata
6. H2O is a Hadoop MapReduce job
7. H2O can only access the files in HDFS that the user has permission to
   access
8. Only the user that started the cluster is authenticated for access to
   the H2O cluster
9. The authenticated user can access the same data in H2O that he could
   access via HDFS

What is being Secured Today
~~~~~~~~~~~~~~~~~~~~~~~~~~~

1. Standard file permissions security is provided by the Operating
   System and by HDFS.

2. The embedded web port in each node of H2O can be secured in two ways:

 +------------------+---------------------------------------+
 | Method           | Description                           |
 +==================+=======================================+
 | HTTPS            | Encrypted socket communication between|
 |                  | the user client and the embedded H2O  |
 |                  | web port.                             |
 +------------------+---------------------------------------+
 | Authentication   | An HTTP Basic Auth username and       |
 |                  | password from the user client.        |
 +------------------+---------------------------------------+

**Note**: Embedded web port HTTPS and authentication may be used separately or together.

3. Internal H2O node-to-H2O node communication can be encrypted.


File Security in H2O
--------------------

H2O is a normal user program. Nothing specifically needs to be done by
the user to get file security for H2O. Operating System and HDFS
permissions "just work".

Standalone H2O
~~~~~~~~~~~~~~

Since H2O is a regular Java program, the files H2O can access are
restricted by the user's Operating System permissions (capabilities).

H2O on Hadoop
~~~~~~~~~~~~~

Since H2O is a regular Hadoop MapReduce program, the files H2O can
access are restricted by the standard HDFS permissions of the user that
starts H2O.

Since H2O is a regular Hadoop MapReduce program, Kerberos (kinit) works
seamlessly. (No code was added to H2O to support Kerberos.)

Sparkling Water on YARN
~~~~~~~~~~~~~~~~~~~~~~~

Similar to H2O on Hadoop, this configuration is H2O on Spark on YARN.
The YARN job inherits the HDFS permissions of the user.

Embedded Web Port (by default port 54321) Security
--------------------------------------------------

For the client side, connection options exist.

For the server side, startup options exist to facilitate security. These
are detailed below.

-------------

HTTPS
~~~~~

HTTPS Client Side
^^^^^^^^^^^^^^^^^

Flow Web UI Client
''''''''''''''''''

When HTTPS is enabled on the server side, the user must provide the
https URI scheme to the browser. No http access will exist.

R Client
''''''''

The following code snippet demonstrates connecting to an H2O cluster
with HTTPS:

::

    h2o.init(ip = "a.b.c.d", port = 54321, https = TRUE, insecure = TRUE)

The underlying HTTPS implementation is provided by RCurl and by
extension libcurl and OpenSSL.

 **Caution:** Certificate checking has not been implemented yet. The insecure flag tells the client to ignore certificate checking. This means your client is exposed to a man-in-the-middle attack. We assume for the time being that in a secure corporate network such attacks are of low concern. Currently, the insecure flag must be set to TRUE so that in some future version of H2O you will confidently know when certificate checking has actually been implemented.

Python Client
'''''''''''''

Not yet implemented. Please contact H2O for an update.

HTTPS Server Side
^^^^^^^^^^^^^^^^^

A `Java Keystore <https://en.wikipedia.org/wiki/Keystore>`_ must be
provided on the server side to enable HTTPS. Keystores can be
manipulated on the command line with the
`keytool <http://docs.oracle.com/javase/6/docs/technotes/tools/solaris/keytool.html>`_
command.

The underlying HTTPS implementation is provided by Jetty 9 and the Java
runtime.

Standalone H2O
''''''''''''''

The following options are available:

::

    -jks <filename>
         Java keystore file

    -jks_pass <password>
         (Default is 'h2oh2o')

Example:

::

    java -jar h2o.jar -jks h2o.jks

H2O on Hadoop
'''''''''''''

The following options are available:

::

    -jks <filename>
         Java keystore file

    -jks_pass <password>
         (Default is 'h2oh2o')

Example:

::

    hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -jks h2o.jks -output hdfsOutputDirectory

Sparkling Water
'''''''''''''''

The following Spark conf properties exist for Java Keystore
configuration:

+--------------------------+-------------------------+
| Spark conf property      | Description             |
+==========================+=========================+
| spark.ext.h2o.jks        | Path to Java Keystore   |
+--------------------------+-------------------------+
| spark.ext.h2o.jks.pass   | JKS password            |
+--------------------------+-------------------------+

Example:

::

    $SPARK_HOME/bin/spark-submit --class water.SparklingWaterDriver --conf spark.ext.h2o.jks=/path/to/h2o.jks sparkling-water-assembly-0.2.17-SNAPSHOT-all.jar

Creating your own self-signed Java Keystore
'''''''''''''''''''''''''''''''''''''''''''

Here is an example of how to create your own self-signed Java Keystore
(mykeystore.jks) with a custom keystore password (mypass) and how to run
standalone H2O using your Keystore:

::

    # Be paranoid and delete any previously existing keystore.
    rm -f mykeystore.jks

    # Generate a new keystore.
    keytool -genkey -keyalg RSA -keystore mykeystore.jks -storepass mypass -keysize 2048
    What is your first and last name?
      [Unknown]:  
    What is the name of your organizational unit?
      [Unknown]:  
    What is the name of your organization?
      [Unknown]:  
    What is the name of your City or Locality?
      [Unknown]:  
    What is the name of your State or Province?
      [Unknown]:  
    What is the two-letter country code for this unit?
      [Unknown]:  
    Is CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown correct?
      [no]:  yes

    Enter key password for <mykey>
        (RETURN if same as keystore password):  

    # Run H2O using the newly generated self-signed keystore.
    java -jar h2o.jar -jks mykeystore.jks -jks_pass mypass

----------------

Kerberos Authentication
~~~~~~~~~~~~~~~~~~~~~~~

Kerberos H2O Client Side
^^^^^^^^^^^^^^^^^^^^^^^^

Flow Web UI Client
''''''''''''''''''

When authentication is enabled, the user will be presented with a
username and password dialog box when attempting to reach Flow.

R Client
''''''''

The following code snippet demonstrates connecting to an H2O cluster
with authentication:

::

    h2o.init(ip = "a.b.c.d", port = 54321, username = "myusername", password = "mypassword")

Python Client
'''''''''''''

For Python, connecting to H2O with authentication is similar:

::

    h2o.init(ip = "a.b.c.d", port = 54321, username = "myusername", password = "mypassword")

Kerberos H2O Server Side
^^^^^^^^^^^^^^^^^^^^^^^^

You must provide a simple configuration file that specifies the Kerberos
login module

Example **kerb.conf**:

::

    krb5loginmodule {
         com.sun.security.auth.module.Krb5LoginModule required
         java.security.krb5.realm="0XDATA.LOC"
         java.security.krb5.kdc="ldap.0xdata.loc";
    };

For more detail about Kerberos configuration:
`Krb5LoginModule <https://docs.oracle.com/javase/7/docs/jre/api/security/jaas/spec/com/sun/security/auth/module/Krb5LoginModule.html>`__,
`Jaas
note <http://docs.oracle.com/javase/7/docs/technotes/guides/security/jgss/tutorials/AcnOnly.html>`__

Standalone H2O
''''''''''''''

The following options are required for Kerberos authentication:

::

    -kerberos_login
          Use Jetty KerberosLoginService

    -login_conf <filename>
          LoginService configuration file

    -user_name <username>
          Override name of user for which access is allowed


Example:

::

    java -jar h2o.jar -kerberos_login -login_conf kerb.conf -user_name kerb_principal

Example (on MacOS):

::

    java -Djava.security.krb5.realm="0XDATA.LOC" -Djava.security.krb5.kdc="ldap.0xdata.loc" -jar h2o.jar -kerberos_login -login_conf kerb.conf -user_name kerb_principal

H2O on Hadoop
'''''''''''''

The following options are available:

::

    -kerberos_login
          Use Jetty KerberosLoginService

    -login_conf <filename>
          LoginService configuration file

    -user_name <username>
          Override name of user for which access is allowed

Example:

::

    hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -kerberos_login -login_conf kerb.conf -output hdfsOutputDirectory -user_name kerb_principal

Sparkling Water
'''''''''''''''

The following Spark conf properties exist for Kerberos configuration:

+--------------------------------+--------------------------------------------+
| Spark conf property            | Description                                |
+================================+============================================+
| spark.ext.h2o.kerberos.login   | Use Jetty Krb5LoginModule                  |
+--------------------------------+--------------------------------------------+
| spark.ext.h2o.login.conf       | LoginService configuration file            |
+--------------------------------+--------------------------------------------+
| spark.ext.h2o.user.name        | Name of user for which access is allowed   |
+--------------------------------+--------------------------------------------+


Example:

::

    $SPARK_HOME/bin/spark-submit --class water.SparklingWaterDriver --conf spark.ext.h2o.kerberos.login=true --conf spark.ext.h2o.user.name=kerb_principal --conf spark.ext.h2o.login.conf=kerb.conf sparkling-water-assembly-0.2.17-SNAPSHOT-all.jar

----------------

LDAP Authentication
~~~~~~~~~~~~~~~~~~~

H2O client and server side configuration for LDAP is discussed below.
Authentication is implemented using `Basic
Auth <https://en.wikipedia.org/wiki/Basic_access_authentication>`__.

LDAP H2O Client Side
^^^^^^^^^^^^^^^^^^^^

Flow Web UI Client
''''''''''''''''''

When authentication is enabled, the user will be presented with a
username and password dialog box when attempting to reach Flow.

R Client
''''''''

The following code snippet demonstrates connecting to an H2O cluster
with authentication:

::

    h2o.init(ip = "a.b.c.d", port = 54321, username = "myusername", password = "mypassword")

Python Client
'''''''''''''

Not yet implemented. Please contact H2O for an update.

LDAP H2O Server Side
^^^^^^^^^^^^^^^^^^^^

An ldap.conf configuration file must be provided by the user. As an
example, this file works for H2O's internal LDAP server. You will
certainly need help from your IT security folks to adjust this
configuration file for your environment.

Example **ldap.conf**:

::

    ldaploginmodule {
        org.eclipse.jetty.jaas.spi.LdapLoginModule required
        debug="true"
        useLdaps="false"
        contextFactory="com.sun.jndi.ldap.LdapCtxFactory"
        hostname="ldap.0xdata.loc"
        port="389"
        bindDn="cn=admin,dc=0xdata,dc=loc"
        bindPassword="0xdata"
        authenticationMethod="simple"
        forceBindingLogin="true"
        userBaseDn="ou=users,dc=0xdata,dc=loc";
    };

See the `Jetty 9 LdapLoginModule
documentation <http://www.eclipse.org/jetty/documentation/current/jaas-support.html>`__
for more information.

Standalone H2O
''''''''''''''

The following options are available:

::

    -ldap_login
          Use Jetty LdapLoginService

    -login_conf <filename>
          LoginService configuration file
         
    -user_name <username>
          Override name of user for which access is allowed

Example:

::

    java -jar h2o.jar -ldap_login -login_conf ldap.conf

    java -jar h2o.jar -ldap_login -login_conf ldap.conf -user_name myLDAPusername

H2O on Hadoop
'''''''''''''

The following options are available:

::

    -ldap_login
          Use Jetty LdapLoginService

    -login_conf <filename>
          LoginService configuration file
         
    -user_name <username>
          Override name of user for which access is allowed

Example:

::

    hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -ldap_login -login_conf ldap.conf -output hdfsOutputDirectory

    hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -ldap_login -login_conf ldap.conf -user_name myLDAPusername -output hdfsOutputDirectory

Sparkling Water
'''''''''''''''

The following Spark conf properties exist for Java keystore
configuration:

+----------------------------+-----------------------------------------------------+
| Spark conf property        | Description                                         |
+============================+=====================================================+
| spark.ext.h2o.ldap.login   | Use Jetty LdapLoginService                          |
+----------------------------+-----------------------------------------------------+
| spark.ext.h2o.login.conf   | LoginService configuration file                     |
+----------------------------+-----------------------------------------------------+
| spark.ext.h2o.user.name    | Override name of user for which access is allowed   |
+----------------------------+-----------------------------------------------------+

Example:

::

    $SPARK_HOME/bin/spark-submit --class water.SparklingWaterDriver --conf spark.ext.h2o.ldap.login=true --conf spark.ext.h2o.login.conf=/path/to/ldap.conf sparkling-water-assembly-0.2.17-SNAPSHOT-all.jar

    $SPARK_HOME/bin/spark-submit --class water.SparklingWaterDriver --conf spark.ext.h2o.ldap.login=true --conf spark.ext.h2o.user.name=myLDAPusername --conf spark.ext.h2o.login.conf=/path/to/ldap.conf sparkling-water-assembly-0.2.17-SNAPSHOT-all.jar


LDAP Authentication and MapR
''''''''''''''''''''''''''''

The following information is for users who authentication with LDAP on MapR, which uses a proprietary Hadoop configuration property that specifies the configuration file. Additional information is available here: `http://doc.mapr.com/display/MapR/mapr.login.conf <http://doc.mapr.com/display/MapR/mapr.login.conf>`__.

In order to make LDAP authentication work, add the ldap.conf definition to the MapR configuration file in **/opt/mapr/conf/mapr.login.conf**.  

Debugging Server-side LDAP issues
'''''''''''''''''''''''''''''''''

To get detailed output from Jetty for LDAP debugging, you need to create the **jetty-logging.properties** file and add it to your classpath.

Example **jetty-logging.properties**:

::

    org.eclipse.jetty.util.log.class=org.eclipse.jetty.util.log.StdErrLog
    org.eclipse.jetty.LEVEL=DEBUG

Standalone H2O example (with **jetty-logging.properties** in the current directory):

::

    java -cp h2o.jar:. water.H2OApp

H2O on Hadoop example (with **jetty-logging.properties** in the current directory):

::

    hadoop jar h2odriver.jar -libjars jetty-logging.properties -n 1 -mapperXmx 5g -output hdfsOutputDirectory

-------------

Pluggable Authentication Module (PAM) Authentication
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This section describes H2O client and server side configuration for `PAM authentication <https://en.wikipedia.org/wiki/Pluggable_authentication_module>`__. 

PAM H2O Client Side
^^^^^^^^^^^^^^^^^^^

Flow UI Client
''''''''''''''

When PAM authentication is enabled, the user will be presented with a username and password dialog box when attempting to reach Flow. 


R Client
''''''''

The following code snippet demonstrates connecting to an H2O cluster
with authentication:

::

    h2o.init(ip = "a.b.c.d", port = 54321, username = "myusername", password = "mypassword")

Python Client
'''''''''''''

For Python, connecting to H2O with authentication is similar:

::

    h2o.init(ip = "a.b.c.d", port = 54321, username = "myusername", password = "mypassword")


PAM H2O Server Side
^^^^^^^^^^^^^^^^^^^

You must provide a simple configuration file that specifies the PAM login module.

**Example pam.conf**

::

  pamloginmodule {
       de.codedo.jaas.PamLoginModule required
       service = h2o;
  };

Note that the name of the service is user configurable, and this name must match the name of the PAM authentication module that you created for the "h2o service".


Standalone H2O
''''''''''''''

The following options are required for PAM authentication:

::

  -pam_login
      Use PAM LoginService

  -login_conf <filename>
        LoginService configuration file
       
  -user_name <username>
        Override name of user for which access is allowed

  -form_auth
        Optionally enable form-based authentication for Flow

  -session_timeout
        If form_auth is enabled, optionally specify the number of minutes 
        that a session can remain idle before the server invalidates the 
        session and requests a new login

**Example**

::

  java -jar h2o.jar -pam_login -login_conf pam.conf -user_name

H2O on Hadoop
'''''''''''''

The following options are available:

::

  -pam_login
      Use PAM LoginService

  -login_conf <filename>
        LoginService configuration file
       
  -user_name <username>
        Override name of user for which access is allowed

  -form_auth
        Optionally enable form-based authentication for Flow

  -session_timeout
        If form_auth is enabled, optionally specify the number of minutes 
        that a session can remain idle before the server invalidates the 
        session and requests a new login


**Example**

::

  hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -pam_login -login_conf pam.conf -output hdfsOutputDirectory -user_name

-------------

Hash File Authentication
~~~~~~~~~~~~~~~~~~~~~~~~

H2O client and server side configuration for a hardcoded hash file is
discussed below. Authentication is implemented using `Basic
Auth <https://en.wikipedia.org/wiki/Basic_access_authentication>`__.

Hash File H2O Client Side
^^^^^^^^^^^^^^^^^^^^^^^^^

Flow Web UI Client
''''''''''''''''''

When authentication is enabled, the user will be presented with a
username and password dialog box when attempting to reach Flow.

R Client
''''''''

The following code snippet demonstrates connecting to an H2O cluster
with authentication:

::

    h2o.init(ip = "a.b.c.d", port = 54321, username = "myusername", password = "mypassword")

Python Client
'''''''''''''

Not yet implemented. Please contact H2O for an update.

Hash File H2O Server Side
^^^^^^^^^^^^^^^^^^^^^^^^^

A **realm.properties** configuration file must be provided by the user.

Example **realm.properties**:

::

    # See http://www.eclipse.org/jetty/documentation/current/configuring-security-secure-passwords.html
    # java -cp h2o.jar org.eclipse.jetty.util.security.Password
    username1: password1
    username2: MD5:6cb75f652a9b52798eb6cf2201057c73

Generate secure passwords using the Jetty secure password generation
tool:

::

    java -cp h2o.jar org.eclipse.jetty.util.security.Password username password

See the `Jetty 9 HashLoginService
documentation <http://wiki.eclipse.org/Jetty/Tutorial/Realms#HashLoginService>`_
and `Jetty 9 Secure Password
HOWTO <http://www.eclipse.org/jetty/documentation/current/configuring-security-secure-passwords.html>`_ for more
information.

Standalone H2O
''''''''''''''

The following options are available:

::

    -hash_login
          Use Jetty HashLoginService
              
    -login_conf <filename>
          LoginService configuration file

Example:

::

    java -jar h2o.jar -hash_login -login_conf realm.properties

H2O on Hadoop
'''''''''''''

The following options are available:

::

    -hash_login
          Use Jetty HashLoginService
              
    -login_conf <filename>
          LoginService configuration file

Example:

::

    hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -hash_login -login_conf realm.propertes -output hdfsOutputDirectory

Sparkling Water
'''''''''''''''

The following Spark conf properties exist for hash login service
configuration:

+----------------------------+-----------------------------------+
| Spark conf property        | Description                       |
+============================+===================================+
| spark.ext.h2o.hash.login   | Use Jetty HashLoginService        |
+----------------------------+-----------------------------------+
| spark.ext.h2o.login.conf   | LoginService configuration file   |
+----------------------------+-----------------------------------+

Example:

::

    $SPARK_HOME/bin/spark-submit --class water.SparklingWaterDriver --conf spark.ext.h2o.hash.login=true --conf spark.ext.h2o.login.conf=/path/to/realm.properties sparkling-water-assembly-0.2.17-SNAPSHOT-all.jar

SSL Internode Security
----------------------

By default, communication between H2O nodes is not encrypted for performance reasons. H2O currently support SSL/TLS authentication (basic handshake authentication) and data encryption for internode communication.

Usage
~~~~~

Hadoop
^^^^^^

The easiest way to enable SSL while running H2O via h2odriver is to pass the ``-internal_secure_connections`` flag. This will tell h2odriver to automatically generate all the necessary files and distribute them to all mappers. This distribution may be secure depending on your YARN configuration.

::

  hadoop jar h2odriver.jar -nodes 4 -mapperXmx 6g -output hdfsOutputDirName -internal_secure_connections


The user can also manually generate keystore/truststore and properties file as described in the `Standalone/AWS`_ section that follows and run the following command to use them instead. In this case, all the files (certificates and properties) have to be distributed to all the mapper nodes by the user.

::

  hadoop jar h2odriver.jar -nodes 4 -mapperXmx 6g -output hdfsOutputDirName -internal_security_conf security.properties


Standalone/AWS
^^^^^^^^^^^^^^

In this case, the user has to generate the keystores, truststores, and properties file manually.

1. Generate public/private keys and distributed them. (Refer to the `Keystore/Truststore Generation`_ section for more information).

2. Create the security properties file. (Refer to the `Configuration`_ section for a full list of parameters.)

 ::

    h2o_ssl_jks_internal=keystore.jks
    h2o_ssl_jks_password=password
    h2o_ssl_jts_internal=truststore.jks
    h2o_ssl_jts_password=password

3. To start an SSL-enabled node, pass the location to the properties file using the ``-internal_security_conf`` flag

 ::

  java -jar h2o.jar -internal_security_conf security.properties

Configuration
~~~~~~~~~~~~~

To enable this feature, set the ``-internal_security_conf`` parameter when starting an H2O node, and point that to a configuration file (key=value format) that contains the following values:

- ``h2o_ssl_jks_internal`` (required): The path (absolute or relative) to the key-store file used for internal SSL communication
- ``h2o_ssl_jks_password`` (required): The password for the internal key-store
- ``h2o_ssl_jts_internal`` (optional): The path (absolute or relative) to the trust-store file used for internal SSL communication. If not present, then ``h2o_ssl_jks_internal`` will be used.
- ``h2o_ssl_jts_password`` (optional): The password to the internal trust-store. If not present, then ``h2o_ssl_jks_password`` will be used.
- ``h2o_ssl_protocol`` (optional): The protocol name used during encrypted communication (supported by JVM). This defaults to TSLv1.2.
- ``h2o_ssl_enabled_algorithms`` (optional): A comma separated list of enabled cipher algorithms. Include only those that are supported by JVM.

This must be set for every node in the cluster. Every node needs to have access to both Java keystore and Java truststore containing appropriate keys and certificates.

This feature should not be used together with the ``-useUDP`` flag, as we currently do not support UDP encryption through DTLS or any other protocol that might result in unencrypted data transfers.

Keystore/Truststore Generation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Keystore/truststore creation and distribution are deployment specific and have to be handled by the end user.

Basic keystore/truststore generation can be done using the keytool program, which ships with Java, documentation can be found `here <https://docs.oracle.com/javase/7/docs/technotes/tools/solaris/keytool.html>`__. Each node should have a key pair generated, and all public keys should be imported into a single truststore, which should be distributed to all the nodes.

The simplest (though not recommended) way would be to call:

::

  keytool -genkeypair -keystore h2o-internal.jks -alias h2o-internal

Then distribute the ``h2o-internal.jks`` file to all the nodes, and set it as both the keystore and truststore in ``ssl.config``. 

A more secure way would be to:

1. Run the same command on each node:
  
 ::

  keytool -genkeypair -keystore h2o-internal.jks -alias h2o-internal

2. Extract the certificate on each node:

 ::

  keytool -export -keystore h2o-internal.jks -alias signFiles -file node<number>.cer

3. Distribute all of the above certificates to each node, and on each node create a truststore containing all of them (or put all certificates on one node, import to truststore and distribute that truststore to each node):

 ::

  keytool -importcert -file node<number>.cer -keystore truststore.jks -alias node<number>


Performance
~~~~~~~~~~~

Turning on SSL may result in performance overhead for settings and algorithms that exchange data between nodes due to encryption/decryption time. Some algorithms might also slower because of this.

Example benchmark on a 5 node cluster (6GB memory per node) working with a 5.8mln row dataset (580MB):

+------------+---------------------+------------------------+
|            | Non SSL             | SSL                    |
+============+=====================+========================+
| Parsing:   | 4.908s              | 5.304s                 |
+------------+---------------------+------------------------+
| GLM model: | 01:39.446           | 01:49.634              |
+------------+---------------------+------------------------+

Caveats and Missing Pieces
~~~~~~~~~~~~~~~~~~~~~~~~~~

 - This feature CANNOT be used together with the ``-useUDP`` flag. We currently do not support DTLS or any other encryption for UDP.
 - Should you start a mixed cloud of SSL and nonSSL nodes, the SSL ones will fail to bootstrap, while the nonSSL ones will become unresponsive.
 - H2O does not provide in-memory data encryption. This might spill data to disk in unencrypted form should swaps to disk occur. As a workaround, an encrypted drive is advised.
 - H2O does not support encryption of data saved to disk, should appropriate flags be enabled. Similar to the previous caveat, the user can use an encrypted drive to work around this issue.
 - H2O supports only SSL and does not support SASL.
