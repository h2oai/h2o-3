Telemetry
=========

Starting with version **3.46.0.12**, H2O-3 sends anonymous usage telemetry to help the team prioritize features and platforms. It is **opt-out** and designed to be invisible: every send is fire-and-forget with a short timeout, so if the receiver is unreachable your code behaves exactly as if telemetry never ran — it never blocks, raises, or retries.

What is sent
------------

- One small ping when you start or connect to H2O (``h2o.init()`` / ``h2o.connect()`` in Python or R, or a standalone ``java -jar h2o.jar`` / ``hadoop jar h2odriver.jar`` cluster), plus one per major action: training, scoring, MOJO and model download, upload, import, parse, AutoML, and model save/load.
- Each ping contains the H2O version, the client (``python`` / ``r`` / ``jvm``), the operating system, an ephemeral session ID regenerated on every start, a timestamp, the algorithm name, and **coarse range buckets** for counts such as rows, columns, durations, and sizes. These counts are reported as ranges rather than exact figures. One notable exception: a small cluster's node count (1–16) is sent exactly, since 1-node vs 4-node is operationally meaningful; larger clusters are bucketed.

What is never sent
------------------

Code, file paths, dataset or model names, column names, parameter values, hostnames, usernames, email addresses, or any user-generated content.

First-run notice
----------------

So that telemetry is never silent, the first time it is active in an environment H2O-3 prints a short one-time notice and then never repeats it. The notice reads approximately:

.. code-block:: text

   H2O-3 collects anonymous usage telemetry (H2O version, OS, algorithm names, and
   coarse usage buckets) to help prioritize features and platforms. It never sends
   your code, data, file paths, or any identifiers.

   To opt out: set H2O_DISABLE_TELEMETRY=1 (or DO_NOT_TRACK=1), or pass
   telemetry=False to h2o.init() / h2o.connect(). See the Telemetry docs for details.

   This notice is shown only once.

Opting out
----------

Any one of the following disables telemetry completely:

- Set an environment variable: ``H2O_DISABLE_TELEMETRY=1`` or ``DO_NOT_TRACK=1``.
- Pass ``telemetry=False`` (Python) or ``telemetry = FALSE`` (R) to ``h2o.init()`` or ``h2o.connect()``.
- For a cluster started directly on the JVM, add ``-Dsys.ai.h2o.telemetry.disabled=true``.

The receiver also honors the standard ``DNT: 1`` (Do Not Track) and ``Sec-GPC: 1``
(Global Privacy Control) request headers: any event arriving with either header set
is dropped and never stored.
