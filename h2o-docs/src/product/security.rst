Security
========

H2O-3 is a machine learning library with a distributed, in-memory compute engine. The engine is launched for one user or one job, runs inside a platform you control, and is driven by client code over a REST control channel. Its security depends on that platform keeping the control channel private. Read `Deployment model and responsibilities <deployment-model.html>`__ first; this page builds on it.

.. _assumptions-threat-model:

.. _what-is-secured-today:

.. _what-is-being-secured-today:

Security model
--------------

Terms
~~~~~

+---------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| Term                                                                                              | Definition                                                                                                                                                                                                                            |
+===================================================================================================+=======================================================================================================================================================================================================================================+
| **H2O-3 cluster (engine)**                                                                        | A collection of H2O-3 nodes that work together on behalf of one user or job.                                                                                                                                                          |
+---------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| **H2O-3 node**                                                                                    | One JVM process running the H2O-3 main class. On YARN, one node is one mapper in one YARN container. On Kubernetes, one node is one pod.                                                                                              |
+---------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| **Control port (REST API)**                                                                       | Each node exposes a REST API, by default on port 54321. Client libraries (Python, R, Java, Scala) use it to drive the engine. If Flow is included in your distribution, it is served from this port too.                              |
+---------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| **Internal communication port**                                                                   | Each node uses the next port (by default 54322) for node-to-node traffic in a proprietary binary protocol. It is unencrypted unless you enable internal TLS. Anyone who can capture this traffic may be able to reconstruct the data. |
+---------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| **Launching platform**                                                                            | Whatever starts the engine and owns its environment: a Python or R session, a YARN or Spark job, a Kubernetes deployment, or a managed platform such as H2O AI Cloud or H2O Driverless AI.                                            |
+---------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+

Threat model
~~~~~~~~~~~~

H2O-3 is designed and supported under the following assumptions:

1. **The engine runs inside a trusted environment controlled by the launching platform.** The platform owns network isolation, identity, and operating-system boundaries.
2. **Only the user or platform that launched the engine can reach its ports.** The control port and internal port are not reachable from shared, public, or untrusted networks.
3. **One engine serves one user or one job.** Engines aren't shared between users. H2O-3 supports authentication but not authorization: once authenticated, a caller has full access to the engine.
4. **The control channel is fully capable.** A caller who can reach the control port, and pass authentication if it's enabled, can run computation, read and write files, and use data-source credentials with the permissions of the engine process. See `Behavior by design <#behavior-by-design>`__.
5. **Denial of service is out of scope.** The engine is built to consume the CPU, memory, and disk it's given, and isn't designed to withstand resource-exhaustion attacks. The platform enforces resource limits.
6. **Traffic is encrypted wherever the network isn't trusted.** Use TLS on the REST API, internal TLS between nodes, or both.
7. **The person or platform that starts H2O-3 configures it correctly.** Security features must be turned on with the correct startup options.
8. **Engines are short-lived.** Data is held in memory and lives only as long as the engine. Sessions don't expire before the engine stops unless you enable ``-form_auth`` with ``-session_timeout``, which ends idle sessions. Data can spill to ``ice_root`` on local disk, and OS swap can write memory to disk, so use encrypted volumes for both.

What H2O-3 secures
~~~~~~~~~~~~~~~~~~

1. **File and data access** is enforced by the operating system and by HDFS or object-store permissions of the user the engine runs as.

2. **The control port** can be protected with:

   ============== ==================================================================================================================================
   Method         Description
   ============== ==================================================================================================================================
   TLS (HTTPS)    Encrypts traffic between the client and the control port.
   Authentication LDAP, Kerberos (HTTP Basic or SPNEGO), PAM, or hash-file credentials. Optional form-based login and idle session timeout for Flow.
   ============== ==================================================================================================================================

   TLS and authentication can be used separately or together.

3. **Node-to-node traffic** can be encrypted with internal TLS.

4. **File paths** reached through the REST API can be restricted with ``-file_deny_glob`` as a defense-in-depth control. It doesn't replace running the engine as an unprivileged user.

Behavior by design
------------------

The REST API is a programmable compute interface. The following behaviors are features. They are frequently reported by security scanners and bug-bounty researchers when the report assumes the control port is exposed to an untrusted caller.

+-----------------------------------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------+
| Capability                                                      | What it does                                                                                                                                                                                             | How to control it                                                                                                           |
+=================================================================+==========================================================================================================================================================================================================+=============================================================================================================================+
| Expression evaluation (Rapids)                                  | Evaluates caller-supplied expressions over frames and models.                                                                                                                                            | Restrict who can reach the control port.                                                                                    |
+-----------------------------------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------+
| User-supplied functions                                         | Custom metrics and custom distributions uploaded by the client run inside the engine.                                                                                                                    | Restrict who can reach the control port. Only upload code you trust.                                                        |
+-----------------------------------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------+
| File import, parse, and export                                  | Reads from and writes to caller-supplied paths on local disk, HDFS, and object storage, including overwriting existing files when ``force=True``. File-name lookups (typeahead) list directory contents. | Run the engine as an unprivileged user with only the access the job needs. Use ``-file_deny_glob`` as an extra restriction. |
+-----------------------------------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------+
| Model import                                                    | Binary model import deserializes Java objects.                                                                                                                                                           | Import only models you created or otherwise trust.                                                                          |
+-----------------------------------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------+
| Data-source connections                                         | JDBC (``import_sql_table``), Hive, and cloud-storage imports use caller-supplied connection details.                                                                                                     | Restrict who can reach the control port. Supply credentials through the platform's secret management.                       |
+-----------------------------------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------+
| Parsing                                                         | Parse setup accepts caller-supplied options, including regular expressions. Very large or compressed inputs consume memory and CPU.                                                                      | Resource limits. Denial of service is out of scope.                                                                         |
+-----------------------------------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------+

How H2O.ai evaluates vulnerability reports
------------------------------------------

H2O.ai reviews every report and every customer scanner finding. Findings generally fall into one of these categories:

+---------------------------------------------------------------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| Category                                                                                                                                                | How it's handled                                                                                                                                                                                                                                                    |
+=========================================================================================================================================================+=====================================================================================================================================================================================================================================================================+
| **A control doesn't work as documented** (for example, an authentication, TLS, or file-restriction bypass)                                              | Treated as a defect and fixed.                                                                                                                                                                                                                                      |
+---------------------------------------------------------------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| **A vulnerability in a bundled third-party dependency**                                                                                                 | Evaluated for reachability and fixed by upgrading the dependency. H2O-3 Secure releases address NIST Critical and High findings. Fixes are delivered in H2O-3 Secure releases and in later H2O-3 OSS releases.                                                      |
+---------------------------------------------------------------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| **An engine capability reported as a vulnerability** (code execution, file access, or model deserialization by a caller who can reach the control port) | Behavior by design. The mitigation is the `deployment model <deployment-model.html>`__: keep the control port private to the launching user or platform.                                                                                                            |
+---------------------------------------------------------------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| **Resource exhaustion**                                                                                                                                 | Out of scope under the threat model. Mitigated by platform resource limits.                                                                                                                                                                                         |
+---------------------------------------------------------------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| **Stale or misattributed records**                                                                                                                      | Some CVE records are published by third-party numbering authorities and aren't updated after a fix ships, or match a different product with a similar name. Check the `change log <https://github.com/h2oai/h2o-3/blob/master/Changes.md>`__ for the fixed version. |
+---------------------------------------------------------------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+

**Scanner tips**

-  Scan the version you run. Findings against older releases are often already fixed.
-  If you only use the Python client against a managed engine, use the ``h2o-client`` package. It doesn't bundle ``h2o.jar``, so the engine's Java dependencies aren't in scope. See `Using H2O-3 only as a client <deployment-model.html#using-h2o-3-only-as-a-client>`__.
-  Some findings come from copies of a library shaded inside another dependency (for example, inside Hadoop or Parquet). These are fixed by upgrading the parent dependency and are tracked like any other dependency finding.

Reporting a vulnerability
~~~~~~~~~~~~~~~~~~~~~~~~~

Report suspected vulnerabilities privately to support@h2o.ai rather than in a public GitHub issue, as described in the repository's ``SECURITY.md``. Reports are most actionable when they include the H2O-3 version, how the engine was launched, the startup options in use, and whether the issue requires the caller to already have access to the control port.

Hardening by launch mode
------------------------

Python or R on a workstation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

``h2o.init()`` starts a local engine bound to the local machine. Keep ``bind_to_localhost=True``, and don't start the engine as an administrator or ``root``. On a shared host (for example, a multi-user JupyterHub server), other local users can reach ``localhost``, so enable authentication there.

Standalone jar
~~~~~~~~~~~~~~

``java -jar h2o.jar`` is intended for development and for platforms that manage the process. The REST API listens on all interfaces unless you restrict it. For anything other than local development:

-  Run as a dedicated, unprivileged user.
-  Bind to a specific interface with ``-ip`` and ``-web_ip`` (for example, ``-web_ip 127.0.0.1`` when the client runs on the same host).
-  Enable authentication and TLS.

.. code:: bash

   java -jar h2o.jar -web_ip 127.0.0.1 \
       -jks /secure/h2o.jks -jks_pass "$H2O_JKS_PASSWORD" \
       -hash_login -login_conf /secure/realm.properties

Hadoop and YARN
~~~~~~~~~~~~~~~

Each H2O-3 node runs as a YARN container under the identity of the user who launched it. File access is governed by that user's HDFS permissions, and Kerberos works through the normal Hadoop mechanisms.

**Data chain of custody**

1. Data lives in HDFS, and HDFS files have permissions.
2. An HDFS user has permission to access certain files. Kerberos (``kinit``) authenticates the user.
3. The user's YARN job inherits the user's permissions and Kerberos credentials.
4. H2O-3 runs as that YARN job, so it can access only the HDFS files that the user can access.
5. With authentication enabled, only the user who started the cluster can use it.
6. The user can therefore access the same data through H2O-3 that they could access through any other Hadoop job.

Recommended options:

-  Authenticate the control port (``-ldap_login``, ``-kerberos_login``, ``-spnego_login``, ``-pam_login``, or ``-hash_login``).
-  Encrypt node-to-node traffic with ``-internal_secure_connections``.
-  Use ``-proxy`` with SPNEGO. When the cluster is launched by a service on the user's behalf, use secure impersonation (``-principal``, ``-keytab``, ``-run_as_user``).
-  Enforce these options for every user with a system-wide ``h2odriver.args`` file (see below).

**Enforcing system-level command-line arguments in h2odriver.jar**

Administrators can create a file of implicit ``h2odriver`` arguments so that every H2O-3 cluster starts with the required security settings.

1. Create the file **/etc/h2o/h2odriver.args**.

2. Add each argument on its own line. For example:

   .. code:: bash

      h2o_ssl_jks_internal=keystore.jks
      h2o_ssl_jks_password=password
      h2o_ssl_jts_internal=truststore.jks
      h2o_ssl_jts_password=password

3. Start H2O-3 as usual:

   .. code:: bash

      hadoop jar h2odriver.jar -mapperXmx 3g -nodes 1

Every user who launches H2O-3 must be able to read this file, so restrict who can modify it, and avoid storing passwords in it where possible. On YARN, ``-internal_secure_connections`` generates internal TLS material per cluster without shared passwords.

Spark (Sparkling Water)
~~~~~~~~~~~~~~~~~~~~~~~

H2O-3 runs inside the user's Spark application on YARN and inherits the user's HDFS permissions. Configure authentication and TLS through the ``spark.ext.h2o.*`` properties listed below (set the ``*.login`` properties to ``true``), and apply the same network controls you use for Spark drivers and executors. For example:

.. code:: bash

   $SPARK_HOME/bin/spark-submit \
       --conf spark.ext.h2o.hash.login=true \
       --conf spark.ext.h2o.login.conf=/path/to/realm.properties \
       --conf spark.ext.h2o.jks=/path/to/h2o.jks \
       --conf spark.ext.h2o.jks.pass="$H2O_JKS_PASSWORD" \
       your-application.py

Kubernetes and containers
~~~~~~~~~~~~~~~~~~~~~~~~~

-  Run the container as a non-root user with a read-only root filesystem and no added capabilities.
-  Use a NetworkPolicy so that only the launching client or platform can reach ports 54321 and 54322.
-  Don't expose the control port through a public Ingress, Route, or load balancer. If users need to reach the engine from outside the cluster, put it behind an authenticating proxy with TLS.

See `Using H2O-3 on Kubernetes <getting-started/kubernetes-users.html#security>`__ for an example.

H2O AI Cloud and H2O Driverless AI
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The platform launches and isolates H2O-3 engines, authenticates users, and manages their lifecycle. Follow the platform's own security documentation.

.. _file-security-in-h2o:

File access
-----------

H2O-3 is a regular user program. The files it can reach are the files its operating-system user (or HDFS user) can reach. This is the primary file-access control:

-  Run the engine as a dedicated, unprivileged user. Never run it as ``root``.
-  Grant that user access only to the data and output locations the job needs.
-  On Hadoop and Spark, rely on HDFS permissions for the launching user.

As a defense-in-depth control, ``-file_deny_glob`` sets a `glob <https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob>`__ pattern for paths the REST API won't read or write. A default deny list covers common system directories. Report a bypass of the deny list as a vulnerability. The deny list complements OS permissions; it doesn't replace them.

.. _embedded-web-port-by-default-port-54321-security:

Control port (by default 54321) security
----------------------------------------

Client connection options and server startup options for each method are described below. With any login method, ``-form_auth`` enables form-based login for Flow, and ``-session_timeout <minutes>`` (requires ``-form_auth``) ends idle sessions.

--------------

HTTPS
~~~~~

HTTPS client side
^^^^^^^^^^^^^^^^^

Flow web UI client
''''''''''''''''''

When HTTPS is enabled on the server, use the ``https`` URI scheme in the browser. Plain HTTP isn't available.

R client
''''''''

.. code:: r

   h2o.init(ip = "a.b.c.d", port = 54321, https = TRUE, insecure = FALSE)

The R client uses RCurl, and by extension libcurl and OpenSSL.

Python client
'''''''''''''

.. code:: python

   h2o.init(ip="a.b.c.d", port=54321, https=True, insecure=False)

The Python client uses the ``requests`` library. Leave ``insecure=False`` so the client verifies the server certificate. Setting ``insecure=True`` disables certificate verification and should be used only for testing. To trust a private certificate authority, pass ``cacert="/path/to/ca-bundle.pem"`` to ``h2o.connect()``.

HTTPS server side
^^^^^^^^^^^^^^^^^

Provide a `Java keystore <https://en.wikipedia.org/wiki/Keystore>`__ to enable HTTPS. Create and manage keystores with the JDK ```keytool`` <https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html>`__ command. HTTPS is served by H2O-3's embedded Jetty web server.

The following options are available for standalone H2O-3 and for H2O-3 on Hadoop:

.. code:: bash

   -jks <filename>
        Java keystore file

   -jks_pass <password>
        Keystore password. The default is 'h2oh2o'; always set your own.

   -jks_alias <alias>
        (Optional) Which certificate from the keystore to use

Standalone example:

.. code:: bash

   java -jar h2o.jar -jks h2o.jks -jks_pass "$H2O_JKS_PASSWORD"

Hadoop example:

.. code:: bash

   hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -jks h2o.jks -jks_pass "$H2O_JKS_PASSWORD" -output hdfsOutputDirectory

Sparkling Water properties:

========================== =========================
Spark conf property        Description
========================== =========================
``spark.ext.h2o.jks``      Path to the Java keystore
``spark.ext.h2o.jks.pass`` Keystore password
========================== =========================

Creating a self-signed Java keystore for testing
''''''''''''''''''''''''''''''''''''''''''''''''

Use a certificate from your organization's certificate authority in production. For testing, you can create a self-signed keystore:

.. code:: bash

   # Remove any existing keystore.
   rm -f mykeystore.jks

   # Generate a new keystore. keytool prompts for the certificate details.
   keytool -genkeypair -keyalg RSA -keysize 2048 -keystore mykeystore.jks -storepass mypass

   # Run H2O-3 with the keystore.
   java -jar h2o.jar -jks mykeystore.jks -jks_pass mypass

--------------

Kerberos authentication (via HTTP Basic)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Kerberos H2O-3 client side
^^^^^^^^^^^^^^^^^^^^^^^^^^

Flow web UI client
''''''''''''''''''

When authentication is enabled, Flow prompts for a username and password.

R client
''''''''

.. code:: r

   h2o.init(ip = "a.b.c.d", port = 54321, username = "myusername", password = "mypassword")

Python client
'''''''''''''

.. code:: python

   h2o.init(ip="a.b.c.d", port=54321, username="myusername", password="mypassword")

HTTP Basic sends credentials with every request. Always combine it with HTTPS.

Kerberos H2O-3 server side
^^^^^^^^^^^^^^^^^^^^^^^^^^

Provide a configuration file that specifies the Kerberos login module.

Example **kerb.conf**:

.. code:: text

   krb5loginmodule {
        com.sun.security.auth.module.Krb5LoginModule required
   };

If the default realm or KDC can't be detected automatically (for example, by resolving the KDC through DNS), set the ``java.security.krb5.realm`` and ``java.security.krb5.kdc`` system properties when you start H2O-3.

For more detail, see the JDK documentation for `Krb5LoginModule <https://docs.oracle.com/en/java/javase/17/docs/api/jdk.security.auth/com/sun/security/auth/module/Krb5LoginModule.html>`__.

Options for standalone H2O-3 and H2O-3 on Hadoop:

.. code:: bash

   -kerberos_login
         Use Jetty KerberosLoginService

   -login_conf <filename>
         LoginService configuration file

   -user_name <username>
         Name of the user for which access is allowed

Standalone examples:

.. code:: bash

   java -jar h2o.jar -kerberos_login -login_conf kerb.conf -user_name kerb_principal

   java -Djava.security.krb5.realm="EXAMPLE.COM" -Djava.security.krb5.kdc="kdc.example.com" \
       -jar h2o.jar -kerberos_login -login_conf kerb.conf -user_name kerb_principal

Hadoop example:

.. code:: bash

   hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -kerberos_login -login_conf kerb.conf -output hdfsOutputDirectory -user_name kerb_principal

Sparkling Water properties:

================================ ============================================
Spark conf property              Description
================================ ============================================
``spark.ext.h2o.kerberos.login`` Use Jetty Krb5LoginModule
``spark.ext.h2o.login.conf``     LoginService configuration file
``spark.ext.h2o.user.name``      Name of the user for which access is allowed
================================ ============================================

--------------

Kerberos authentication (via kinit/SPNEGO)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

SPNEGO client side
^^^^^^^^^^^^^^^^^^

Flow web UI client
''''''''''''''''''

Modern browsers support Kerberos authentication. When you open Flow, the server responds with ``401`` and a ``Negotiate`` header, and the browser uses the ticket acquired with ``kinit`` on the client machine.

R client
''''''''

.. code:: r

   h2o.init(ip = "a.b.c.d", port = 54321, use_spnego = TRUE)

**Limitation:** The R client uses RCurl, which doesn't let you specify the service principal. The principal is generated from the template ``HTTP/HOSTNAME@DOMAIN``.

Python client
'''''''''''''

.. code:: python

   from h2o.auth import SpnegoAuth

   h2o.connect(ip="a.b.c.d", port=54321, auth=SpnegoAuth(service_principal="HTTP/h2o_server@EXAMPLE.COM"))

**Limitation:** Connecting to a SPNEGO-configured server is supported only through ``h2o.connect``, not ``h2o.init``.

SPNEGO server side
^^^^^^^^^^^^^^^^^^

Create a keytab on the server containing the key for the service principal. The client must use the same service principal.

Example **spnego.conf**:

.. code:: text

   com.sun.security.jgss.initiate {
       com.sun.security.auth.module.Krb5LoginModule required
       principal="HTTP/h2o_server@EXAMPLE.COM"
       keyTab="/srv/h2o.keytab"
       useKeyTab=true
       storeKey=true
       isInitiator=false;
   };

   com.sun.security.jgss.accept {
       com.sun.security.auth.module.Krb5LoginModule required
       principal="HTTP/h2o_server@EXAMPLE.COM"
       keyTab="/srv/h2o.keytab"
       useKeyTab=true
       storeKey=true
       isInitiator=false;
   };

Example **spnego.properties**:

.. code:: text

   targetName=HTTP/h2o_server@EXAMPLE.COM

Options for standalone H2O-3 and H2O-3 on Hadoop:

.. code:: bash

   -spnego_login
         Use Jetty SPNEGO Login Service

   -user_name <username>
         Principal for which access is allowed; must be the full Kerberos name (name/path@DOMAIN)

   -login_conf <filename>
         Path to spnego.conf

   -spnego_properties <filename>
         Path to spnego.properties

Standalone example:

.. code:: bash

   java -jar h2o.jar \
       -spnego_login -user_name principal@DOMAIN \
       -login_conf /path/to/spnego.conf \
       -spnego_properties /path/to/spnego.properties

Hadoop example:

.. code:: bash

   hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -output hdfsOutputDirectory \
       -proxy -spnego_login -user_name principal@DOMAIN \
       -login_conf /path/to/spnego.conf \
       -spnego_properties /path/to/spnego.properties

**Limitation:** A Kerberos service principal is tied to a hostname, so on Hadoop use SPNEGO only with the ``-proxy`` option.

--------------

LDAP authentication
~~~~~~~~~~~~~~~~~~~

LDAP authentication uses `HTTP Basic <https://en.wikipedia.org/wiki/Basic_access_authentication>`__. Always combine it with HTTPS.

LDAP client side
^^^^^^^^^^^^^^^^

Flow web UI client
''''''''''''''''''

When authentication is enabled, Flow prompts for a username and password.

R client
''''''''

.. code:: r

   h2o.init(ip = "a.b.c.d", port = 54321, username = "myusername", password = "mypassword")

Python client
'''''''''''''

.. code:: python

   h2o.init(ip="a.b.c.d", port=54321, username="myusername", password="mypassword")

LDAP server side
^^^^^^^^^^^^^^^^

Provide an **ldap.conf** file. Work with your directory administrators to adapt it to your environment. Use LDAPS, turn off debug output in production, and restrict read access to the file because it contains the bind password.

Example **ldap.conf**:

.. code:: text

   ldaploginmodule {
       ai.h2o.org.eclipse.jetty.plus.jaas.spi.LdapLoginModule required
       debug="false"
       useLdaps="true"
       contextFactory="com.sun.jndi.ldap.LdapCtxFactory"
       hostname="ldap.example.com"
       port="636"
       bindDn="cn=h2o-bind,ou=service,dc=example,dc=com"
       bindPassword="<bind-password>"
       authenticationMethod="simple"
       forceBindingLogin="true"
       userBaseDn="ou=users,dc=example,dc=com";
   };

Options for standalone H2O-3 and H2O-3 on Hadoop:

.. code:: bash

   -ldap_login
         Use Jetty LdapLoginService

   -login_conf <filename>
         LoginService configuration file

   -user_name <username>
         Name of the user for which access is allowed

Standalone examples:

.. code:: bash

   java -jar h2o.jar -ldap_login -login_conf ldap.conf

   java -jar h2o.jar -ldap_login -login_conf ldap.conf -user_name myLDAPusername

Hadoop examples:

.. code:: bash

   hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -ldap_login -login_conf ldap.conf -output hdfsOutputDirectory

   hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -ldap_login -login_conf ldap.conf -user_name myLDAPusername -output hdfsOutputDirectory

Sparkling Water properties:

============================ ============================================
Spark conf property          Description
============================ ============================================
``spark.ext.h2o.ldap.login`` Use Jetty LdapLoginService
``spark.ext.h2o.login.conf`` LoginService configuration file
``spark.ext.h2o.user.name``  Name of the user for which access is allowed
============================ ============================================

LDAP authentication and MapR
''''''''''''''''''''''''''''

MapR uses a proprietary Hadoop configuration property to locate the login configuration. Add the ``ldap.conf`` definition to **/opt/mapr/conf/mapr.login.conf**.

Debugging server-side LDAP issues
'''''''''''''''''''''''''''''''''

To get detailed Jetty output, create a **jetty-logging.properties** file and add it to the classpath. Remove it when you're done, because debug output can include sensitive details.

.. code:: text

   org.eclipse.jetty.util.log.class=org.eclipse.jetty.util.log.StdErrLog
   org.eclipse.jetty.LEVEL=DEBUG

Standalone (with **jetty-logging.properties** in the current directory):

.. code:: bash

   java -cp h2o.jar:. water.H2OApp

Hadoop (with **jetty-logging.properties** in the current directory):

.. code:: bash

   hadoop jar h2odriver.jar -libjars jetty-logging.properties -n 1 -mapperXmx 5g -output hdfsOutputDirectory

--------------

Pluggable Authentication Module (PAM) authentication
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

PAM client side
^^^^^^^^^^^^^^^

When PAM authentication is enabled, Flow prompts for a username and password. The R and Python clients pass ``username`` and ``password`` to ``h2o.init()``, as shown for LDAP. Always combine PAM with HTTPS.

PAM server side
^^^^^^^^^^^^^^^

Provide a configuration file that specifies the PAM login module.

Example **pam.conf**:

.. code:: text

   pamloginmodule {
        de.codedo.jaas.PamLoginModule required
        service = h2o;
   };

The service name is configurable and must match the PAM service you created for H2O-3.

Options for standalone H2O-3 and H2O-3 on Hadoop:

.. code:: bash

   -pam_login
         Use PAM LoginService

   -login_conf <filename>
         LoginService configuration file

   -user_name <username>
         Name of the user for which access is allowed

   -form_auth
         (Optional) Enable form-based authentication for Flow

   -session_timeout <minutes>
         (Optional, requires -form_auth) Minutes a session can stay idle before the server requires a new login

Standalone example:

.. code:: bash

   java -jar h2o.jar -pam_login -login_conf pam.conf -user_name myusername

Hadoop example:

.. code:: bash

   hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -pam_login -login_conf pam.conf -output hdfsOutputDirectory -user_name myusername

--------------

Hash file authentication
~~~~~~~~~~~~~~~~~~~~~~~~

Hash file authentication checks credentials against a local **realm.properties** file and uses `HTTP Basic <https://en.wikipedia.org/wiki/Basic_access_authentication>`__. It's suited to single-user and test deployments. Always combine it with HTTPS.

Hash file client side
^^^^^^^^^^^^^^^^^^^^^

Flow prompts for a username and password. The R and Python clients pass ``username`` and ``password`` to ``h2o.init()``, as shown for LDAP.

Hash file server side
^^^^^^^^^^^^^^^^^^^^^

Example **realm.properties**:

.. code:: text

   username1: MD5:6cb75f652a9b52798eb6cf2201057c73

Generate the hashed entry with Jetty's password tool:

.. code:: bash

   java -cp h2o.jar org.eclipse.jetty.util.security.Password username password

Don't store plain-text passwords in this file, and restrict read access to it.

Options for standalone H2O-3 and H2O-3 on Hadoop:

.. code:: bash

   -hash_login
         Use Jetty HashLoginService

   -login_conf <filename>
         LoginService configuration file

Standalone example:

.. code:: bash

   java -jar h2o.jar -hash_login -login_conf realm.properties

Hadoop example:

.. code:: bash

   hadoop jar h2odriver.jar -n 3 -mapperXmx 10g -hash_login -login_conf realm.properties -output hdfsOutputDirectory

Sparkling Water properties:

============================ ===============================
Spark conf property          Description
============================ ===============================
``spark.ext.h2o.hash.login`` Use Jetty HashLoginService
``spark.ext.h2o.login.conf`` LoginService configuration file
============================ ===============================

.. _ssl-internode-security:

Internal (node-to-node) TLS
---------------------------

Node-to-node traffic isn't encrypted by default, for performance. H2O-3 supports TLS for authentication (handshake) and encryption of internal communication.

Usage
~~~~~

Hadoop
^^^^^^

The simplest option is the ``-internal_secure_connections`` flag. ``h2odriver`` generates the required keystore, truststore, and properties file and distributes them to the mappers. Whether that distribution is protected depends on your YARN configuration.

.. code:: bash

   hadoop jar h2odriver.jar -nodes 4 -mapperXmx 6g -output hdfsOutputDirName -internal_secure_connections

You can also generate the files yourself, as described in `Standalone <#standalone>`__, and pass them with ``-internal_security_conf``. In that case you must distribute the certificates and properties file to every mapper node.

.. code:: bash

   hadoop jar h2odriver.jar -nodes 4 -mapperXmx 6g -output hdfsOutputDirName -internal_security_conf security.properties

Standalone
^^^^^^^^^^

1. Generate and distribute the keys. See `Keystore and truststore generation <#keystore-and-truststore-generation>`__.

2. Create the security properties file. See `Configuration <#configuration>`__.

   .. code:: text

      h2o_ssl_jks_internal=keystore.jks
      h2o_ssl_jks_password=password
      h2o_ssl_jts_internal=truststore.jks
      h2o_ssl_jts_password=password

3. Start each node with ``-internal_security_conf``:

   .. code:: bash

      java -jar h2o.jar -internal_security_conf security.properties

Configuration
~~~~~~~~~~~~~

Pass ``-internal_security_conf <file>`` when you start each node. The file uses ``key=value`` format:

-  ``h2o_ssl_jks_internal`` (required): Path to the keystore used for internal TLS.
-  ``h2o_ssl_jks_password`` (required): Keystore password.
-  ``h2o_ssl_jts_internal`` (optional): Path to the truststore. Defaults to ``h2o_ssl_jks_internal``.
-  ``h2o_ssl_jts_password`` (optional): Truststore password. Defaults to ``h2o_ssl_jks_password``.
-  ``h2o_ssl_protocol`` (optional): Protocol name supported by the JVM. Defaults to ``TLSv1.2``.
-  ``h2o_ssl_enabled_algorithms`` (optional): Comma-separated list of enabled cipher suites supported by the JVM.

Every node needs the same configuration and access to a keystore and truststore with the appropriate keys and certificates. Restrict read access to the properties file, because it contains passwords.

Keystore and truststore generation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Creating and distributing keystores is deployment specific. Use the JDK ```keytool`` <https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html>`__. Generate a key pair on each node and import all public certificates into one truststore that every node uses:

1. On each node, generate a key pair:

   .. code:: bash

      keytool -genkeypair -keyalg RSA -keysize 2048 -keystore h2o-internal.jks -alias h2o-internal

2. On each node, export the certificate:

   .. code:: bash

      keytool -export -keystore h2o-internal.jks -alias h2o-internal -file node<number>.cer

3. Import every certificate into a truststore and distribute it to all nodes:

   .. code:: bash

      keytool -importcert -file node<number>.cer -keystore truststore.jks -alias node<number>

A single shared keystore that serves as both keystore and truststore on every node also works, but it isn't recommended.

Performance
~~~~~~~~~~~

TLS adds encryption overhead for algorithms that exchange data between nodes. Example benchmark on a 5-node cluster (6 GB per node) with a 5.8 million row (580 MB) dataset:

========= =========== =========
\         Without TLS With TLS
========= =========== =========
Parsing   4.908 s     5.304 s
GLM model 01:39.446   01:49.634
========= =========== =========

Caveats
~~~~~~~

-  A cluster that mixes TLS and non-TLS nodes won't form: TLS nodes fail to bootstrap, and non-TLS nodes become unresponsive.
-  H2O-3 doesn't encrypt data in memory. Memory swapped to disk is unencrypted, so use encrypted volumes.
-  H2O-3 doesn't encrypt data it writes to disk (for example, ``ice_root`` spill files, exports, and logs). Use encrypted volumes.
-  H2O-3 supports TLS only. It doesn't support SASL.
