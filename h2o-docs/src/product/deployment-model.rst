Deployment model and responsibilities
=====================================

H2O-3 is a machine learning library. Your code (Python, R, Java, or Scala) drives a distributed, in-memory compute engine, the H2O-3 cluster, that runs in one or more JVMs. The engine is launched on demand for a user or a job, does the work, and is shut down.

H2O-3 isn't designed to run as a shared or public network service that accepts requests from arbitrary callers. The engine always runs inside something else: your notebook, your Hadoop or Spark job, your Kubernetes namespace, or a managed platform such as H2O AI Cloud or H2O Driverless AI. That surrounding platform owns the network, identity, and operating-system boundaries.

This page describes that model. The `Security <security.html>`__ page covers the threat model and the configuration options in detail.

How the engine is launched
--------------------------

+-----------------------------------------------------------------+---------------------------------------------------------------------------------------------+--------------------------------------------------------------------------------------------------------------------------------------+
| Launch mode                                                     | Who starts the engine                                                                       | Who can reach it by default                                                                                                          |
+=================================================================+=============================================================================================+======================================================================================================================================+
| **Python or R client on a workstation** (``h2o.init()``)        | The client library starts a local JVM for the session.                                      | Local machine only. ``bind_to_localhost`` defaults to ``True``.                                                                      |
+-----------------------------------------------------------------+---------------------------------------------------------------------------------------------+--------------------------------------------------------------------------------------------------------------------------------------+
| **Hadoop / YARN** (``hadoop jar h2odriver.jar``)                | The user's YARN job. Each H2O-3 node is a mapper that runs with the user's Hadoop identity. | Hosts on the cluster network. Protect with Kerberos, H2O-3 authentication, and cluster network controls.                             |
+-----------------------------------------------------------------+---------------------------------------------------------------------------------------------+--------------------------------------------------------------------------------------------------------------------------------------+
| **Spark** (Sparkling Water)                                     | The user's Spark application.                                                               | Hosts on the Spark cluster network, as for any Spark application.                                                                    |
+-----------------------------------------------------------------+---------------------------------------------------------------------------------------------+--------------------------------------------------------------------------------------------------------------------------------------+
| **Kubernetes**                                                  | A StatefulSet deployed by the platform team or a platform operator.                         | Whatever the namespace network policy allows.                                                                                        |
+-----------------------------------------------------------------+---------------------------------------------------------------------------------------------+--------------------------------------------------------------------------------------------------------------------------------------+
| **H2O AI Cloud, H2O Driverless AI**                             | The platform.                                                                               | Only the platform and the user's authenticated session.                                                                              |
+-----------------------------------------------------------------+---------------------------------------------------------------------------------------------+--------------------------------------------------------------------------------------------------------------------------------------+
| **Standalone jar** (``java -jar h2o.jar``)                      | A developer or a platform process.                                                          | All network interfaces unless ``-ip`` / ``-web_ip`` restrict it. Intended for development and for platforms that manage the process. |
+-----------------------------------------------------------------+---------------------------------------------------------------------------------------------+--------------------------------------------------------------------------------------------------------------------------------------+

In every mode the engine belongs to **one user or one job**, and lives only as long as that session.

What the REST API is
--------------------

Each H2O-3 node exposes a REST API on port 54321 (by default). This is the **control channel** between your client code and the engine. It is the same interface the Python and R clients use for every operation. Node-to-node traffic uses the next port (54322 by default).

Because the REST API is how your code tells the engine what to do, it is intentionally powerful. A caller that can reach the control port can:

-  **Run computation it defines.** Rapids expressions, and user-supplied functions such as custom metrics and custom distributions, execute inside the engine.
-  **Read and write files at paths it chooses.** Import, parse, and export frames, models, logs, and MOJOs to local disk, HDFS, S3, GCS, and other storage, with the permissions of the engine process.
-  **Load models it provides.** Binary model import deserializes Java objects and is meant for models you created.
-  **Connect to data sources it names.** JDBC, Hive, and cloud storage connections use caller-supplied connection details.
-  **Use the resources it was given.** Training jobs are expected to consume the CPU, memory, and disk allocated to the engine.

These are the product's features. They are equivalent to what a Python interpreter, a Spark driver, or a JDBC connection can do for the person running it.

.. warning::

   Anyone who can reach an H2O-3 control port, and pass authentication if it's enabled, can execute code and access files with the engine's operating-system and storage permissions. Treat the port the way you would treat a shell on the host, and make sure only the user or platform that launched the engine can reach it.

Why this matters for vulnerability reports
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

H2O-3's source code is public, and H2O.ai welcomes security research on it. Because the capabilities above are reachable through the REST API, reports and scanner findings sometimes describe them as vulnerabilities: for example, "an unauthenticated user can import a file from an arbitrary path," "a user can execute code through a custom function," or "a crafted request can exhaust server memory."

When a report assumes the control port is reachable by an untrusted party, it's describing a deployment outside this model, and the behavior it reports is the intended behavior of the engine. The most useful reports show an issue that holds within the deployment model. H2O.ai evaluates every report. Reports that show a control failing to do what it's documented to do, a bypass of authentication or TLS, or an issue in a bundled dependency are treated as defects and fixed. See `How H2O.ai evaluates vulnerability reports <security.html#how-h2o-ai-evaluates-vulnerability-reports>`__.

Shared responsibility
---------------------

+-----------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+
| Area                                                            | H2O-3 provides                                                                                                                                                        | The launching platform provides                                                                                                                |
+=================================================================+=======================================================================================================================================================================+================================================================================================================================================+
| **Network exposure**                                            | Options to bind the REST API and internal communication to specific interfaces (``-ip``, ``-web_ip``, ``-network``).                                                  | Isolation so that only the launching client or platform can reach ports 54321 and 54322. No exposure to shared, public, or untrusted networks. |
+-----------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+
| **Identity and authentication**                                 | Optional authentication on the REST API: LDAP, Kerberos, SPNEGO, PAM, and hash-file logins.                                                                           | The user's identity, and either H2O-3 authentication or an authenticating proxy in front of the engine.                                        |
+-----------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+
| **Authorization**                                               | None within a cluster. An authenticated user has full access.                                                                                                         | One engine per user or job. Never share an engine between users.                                                                               |
+-----------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+
| **Process and file permissions**                                | A file-path deny list (``-file_deny_glob``) as a defense-in-depth control.                                                                                            | A dedicated, unprivileged OS user (never ``root``), and HDFS or object-store permissions limited to the data the user is entitled to.          |
+-----------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+
| **Encryption in transit**                                       | TLS for the REST API and for node-to-node traffic.                                                                                                                    | Certificates and keystores, or TLS termination at the platform edge.                                                                           |
+-----------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+
| **Encryption at rest**                                          | None. Data is held in memory and may spill to ``ice_root``.                                                                                                           | Encrypted volumes for ``ice_root``, logs, and swap.                                                                                            |
+-----------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+
| **Availability**                                                | Nothing beyond the resources it's given. The engine isn't designed to withstand denial-of-service attacks.                                                            | Resource quotas (YARN queues, Kubernetes limits) and restarting engines as needed.                                                             |
+-----------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+
| **Lifecycle**                                                   | Engines that start and stop cleanly.                                                                                                                                  | Short-lived engines, stopped when the session or job ends.                                                                                     |
+-----------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+
| **Software updates**                                            | Regular releases with fixes and dependency updates. H2O-3 Secure, the commercially supported tier, adds releases that address NIST Critical and High vulnerabilities. | Staying on a current release.                                                                                                                  |
+-----------------------------------------------------------------+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+

Deployment checklist
--------------------

Before running H2O-3 anywhere other than your own workstation:

1. **Keep the control port private.** Only the launching client or platform should be able to reach ports 54321 and 54322. Don't publish them through a public load balancer, Ingress, or Route.
2. **Run one engine per user or job.** Don't share a running engine between users.
3. **Run as an unprivileged user.** Never run the engine as ``root``, and give that user only the file and storage access the job needs.
4. **Enable authentication and TLS** when the network between the client and the engine isn't fully trusted. See `Security <security.html>`__.
5. **Bind to specific interfaces** with ``-ip``, ``-web_ip``, or ``-network`` instead of listening on every interface.
6. **Import only trusted models and code.** Binary models and user-supplied functions execute inside the engine.
7. **Set resource limits** with YARN queues, Kubernetes requests and limits, or ``-Xmx``.
8. **Stop engines** when the work is done.
9. **Stay current.** Run a current release. For commercial CVE patching and support, use H2O-3 Secure.

Using H2O-3 only as a client
----------------------------

If your code only connects to engines that a platform runs (for example, H2O AI Cloud), install the ``h2o-client`` Python package instead of ``h2o``. It contains the Python API without the bundled ``h2o.jar``, so it can't start a local engine, and dependency scanners won't report findings from the engine's Java dependencies.

.. code:: bash

   pip install h2o-client
