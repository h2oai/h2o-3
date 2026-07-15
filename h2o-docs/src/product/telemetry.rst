Telemetry
=========

Starting with version **3.46.0.12**, H2O-3 can send anonymous usage telemetry to help the team prioritize features and platforms. It is **opt-in and off by default** — nothing is sent unless you turn it on. It is also designed to be invisible when enabled: every send is fire-and-forget with a short timeout, so if the receiver is unreachable your code behaves exactly as if telemetry never ran — it never blocks, raises, or retries.

What is sent (when enabled)
---------------------------

- One small ping when you start or connect to H2O (``h2o.init()`` / ``h2o.connect()`` in Python or R, or a standalone ``java -jar h2o.jar`` / ``hadoop jar h2odriver.jar`` cluster), plus one per major action: training, scoring, MOJO and model download, upload, import, parse, AutoML, and model save/load.
- Each ping contains the H2O version, the client (``python`` / ``r`` / ``jvm``), the operating system, an ephemeral session ID regenerated on every start, a timestamp, the algorithm name, and **coarse range buckets** for counts such as rows, columns, durations, and sizes. These counts are reported as ranges rather than exact figures. One notable exception: a small cluster's node count (1–16) is sent exactly, since 1-node vs 4-node is operationally meaningful; larger clusters are bucketed.

What is never sent
------------------

Code, file paths, dataset or model names, column names, parameter values, hostnames, usernames, email addresses, or any user-generated content.

Enabling telemetry
------------------

Telemetry is **off by default**. Any one of the following turns it on:

**Per session** (applies to the current process only):

- Pass ``telemetry=True`` (Python) or ``telemetry = TRUE`` (R) to ``h2o.init()`` or ``h2o.connect()``.

**Persistent** (remembered across sessions):

- **Programmatic switch** — use the setter ``h2o.set_telemetry()`` to change telemetry and the getter ``h2o.telemetry_enabled()`` to read the current state. Both are available in the Python and R clients; the setting applies immediately and is saved under ``~/.h2oai/telemetry`` so later sessions honor it.

  Python:

  .. code-block:: python

     h2o.set_telemetry(True)     # opt in (persisted across sessions)
     h2o.set_telemetry(False)    # opt back out
     h2o.telemetry_enabled()     # -> True or False

  R:

  .. code-block:: r

     h2o.set_telemetry(TRUE)     # opt in (persisted across sessions)
     h2o.set_telemetry(FALSE)    # opt back out
     h2o.telemetry_enabled()     # -> TRUE or FALSE

- **Config file** — add a ``general.telemetry`` key to ``~/.h2oconfig`` in your home directory:

  .. code-block:: ini

     [general]
     telemetry = true

For a cluster started directly on the JVM (``java -jar h2o.jar`` / ``hadoop jar h2odriver.jar``), the server administrator enables it with ``-Dsys.ai.h2o.telemetry.enabled=true`` or by setting ``H2O_ENABLE_TELEMETRY=1`` in its environment.

Turning it off again
--------------------

Since telemetry is off by default you normally don't need to do anything. To turn it off after enabling it — or to guarantee it stays off regardless of any of the settings above — use any of:

- Pass ``telemetry=False`` (Python) / ``telemetry = FALSE`` (R) to ``h2o.init()`` / ``h2o.connect()`` for the current session, or ``h2o.set_telemetry(False)`` to persist it.
- Set ``general.telemetry = false`` in ``~/.h2oconfig``.
- Set ``DO_NOT_TRACK=1`` (the cross-tool standard from `consoledonottrack.com <https://consoledonottrack.com>`__). This is a **hard opt-out**: it always wins over every enable setting above, and is honored by the Python client, the R client, and the JVM server.
- For a JVM-launched cluster, ``-Dsys.ai.h2o.telemetry.disabled=true`` (also a hard opt-out).

The receiver also honors the standard ``DNT: 1`` (Do Not Track) and ``Sec-GPC: 1``
(Global Privacy Control) request headers: any event arriving with either header set
is dropped and never stored.
