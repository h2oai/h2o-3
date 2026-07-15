Telemetry
=========

Starting with version **3.46.0.12**, H2O-3 can send anonymous usage telemetry to help the team prioritize features and platforms. It is **opt-in and off by default** — nothing is sent unless you turn it on. It is also designed to be invisible when enabled: every send is fire-and-forget with a short timeout, so if the receiver is unreachable your code behaves exactly as if telemetry never ran — it never blocks, raises, or retries.

It is limited to operational and runtime characteristics, and is used only in aggregate to help H2O.ai operate, secure, support, maintain, and improve H2O-3 — understanding common deployment patterns, prioritizing compatibility and support efforts, and detecting abuse or malicious activity. It is **not** used for sales prospecting, lead identification, or user surveillance.

What is sent (when enabled)
---------------------------

- One small ping when you start or connect to H2O (``h2o.init()`` / ``h2o.connect()`` in Python or R, or a standalone ``java -jar h2o.jar`` / ``hadoop jar h2odriver.jar`` cluster), plus one per major action: training, scoring, MOJO and model download, upload, import, parse, AutoML, and model save/load.
- Each ping contains the H2O version, the client (``python`` / ``r`` / ``jvm``), the operating system, an ephemeral session ID regenerated on every start, a timestamp, the algorithm name, and **coarse range buckets** for counts such as rows, columns, durations, and sizes. These counts are reported as ranges rather than exact figures. One notable exception: a small cluster's node count (1–16) is sent exactly, since 1-node vs 4-node is operationally meaningful; larger clusters are bucketed.

What is never sent
------------------

Code, dataset contents, prompts, model inputs or outputs, training data, prediction values, file paths or URLs, dataset or model names, column names, parameter values, hostnames, usernames, email addresses, precise location, or any other customer business data.

Source IP addresses are inherently visible to any HTTPS request; the receiver may use them transiently to derive a coarse geographic region and for network attribution, and does not persist or store them. The telemetry payload itself contains no location data.

Enabling telemetry
------------------

Telemetry is **off by default**. Turn it on with any of the following.

**Python and R clients**

- **Per session** — pass ``telemetry=True`` (Python) or ``telemetry = TRUE`` (R) to ``h2o.init()`` or ``h2o.connect()``.
- **Persistent** — use the setter ``h2o.set_telemetry()`` to change it and the getter ``h2o.telemetry_enabled()`` to read the current state. The setting applies immediately and is saved under ``~/.h2oai/telemetry`` so later sessions honor it.

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

**Standalone / Hadoop cluster (JVM)**

A cluster started directly on the JVM (``java -jar h2o.jar`` / ``hadoop jar h2odriver.jar``) is also off by default. Enable it by explicitly setting the disable flag to false:

.. code-block:: bash

   java -Dsys.ai.h2o.telemetry.disabled=false -jar h2o.jar

Turning it off / keeping it off
-------------------------------

Since telemetry is off by default you normally don't need to do anything. To turn it off after enabling it — or to force it off regardless of the settings above — use any of:

- **Clients** — pass ``telemetry=False`` (Python) / ``telemetry = FALSE`` (R) for the current session, or ``h2o.set_telemetry(False)`` to persist it, or set ``general.telemetry = false`` in ``~/.h2oconfig``.
- **JVM cluster** — ``-Dsys.ai.h2o.telemetry.disabled=true`` (the default, off, is also honored when the flag is unset).
- **Any environment** — set ``DO_NOT_TRACK=1`` (the cross-tool standard from `consoledonottrack.com <https://consoledonottrack.com>`__). This is a **hard opt-out**: it always wins over every enable setting above and is honored by the Python client, the R client, and the JVM server.

The receiver also honors the standard ``DNT: 1`` (Do Not Track) and ``Sec-GPC: 1``
(Global Privacy Control) request headers: any event arriving with either header set
is dropped and never stored.
