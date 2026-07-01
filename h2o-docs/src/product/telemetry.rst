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

Opting out
----------

Telemetry is on by default. Any one of the following disables it — if **any** opt-out is in effect, nothing is sent, and ``DO_NOT_TRACK`` always takes precedence over the other settings.

**Per session** (applies to the current process only):

- Pass ``telemetry=False`` (Python) or ``telemetry = FALSE`` (R) to ``h2o.init()`` or ``h2o.connect()``.

**Persistent** (remembered across sessions):

- **Environment variable** — set ``DO_NOT_TRACK=1`` (the cross-tool standard from `consoledonottrack.com <https://consoledonottrack.com>`__). It always wins, and is honored by the Python client, the R client, and the JVM server.
- **Programmatic switch** — use the setter ``h2o.set_telemetry()`` to change telemetry and the getter ``h2o.telemetry_enabled()`` to read the current state. Both are available in the Python and R clients; the setting applies immediately and is saved under ``~/.h2oai/telemetry`` so later sessions honor it.

  Python:

  .. code-block:: python

     h2o.set_telemetry(False)    # opt out (persisted across sessions)
     h2o.set_telemetry(True)     # opt back in
     h2o.telemetry_enabled()     # -> True or False

  R:

  .. code-block:: r

     h2o.set_telemetry(FALSE)    # opt out (persisted across sessions)
     h2o.set_telemetry(TRUE)     # opt back in
     h2o.telemetry_enabled()     # -> TRUE or FALSE

- **Config file** — add a ``general.telemetry`` key to ``~/.h2oconfig`` in your home directory:

  .. code-block:: ini

     [general]
     telemetry = false

For a cluster started directly on the JVM (``java -jar h2o.jar`` / ``hadoop jar h2odriver.jar``), add ``-Dsys.ai.h2o.telemetry.disabled=true`` or set ``DO_NOT_TRACK=1`` in its environment.

The receiver also honors the standard ``DNT: 1`` (Do Not Track) and ``Sec-GPC: 1``
(Global Privacy Control) request headers: any event arriving with either header set
is dropped and never stored.
