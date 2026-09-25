Docker users
============

A container is a convenient way to package the H2O-3 engine for a platform to launch. The container runs the engine for one user or job, and the REST API on port 54321 is the control channel for the client code that drives it. Keep that port private to the client. See `Deployment model and responsibilities <../deployment-model.html>`__.

This walkthrough describes:

-  Building an image that runs H2O-3 as an unprivileged user
-  Running the container with the control port bound to the local host
-  Connecting from Python or R

For H2O-3 Secure deployments, use the container image supplied with your enterprise distribution. Contact enterprise@h2o.ai.

Prerequisites
-------------

-  Linux kernel version 3.8+ or macOS
-  Latest version of Docker installed and configured, with the Docker daemon running
-  ``h2o.jar`` from the `H2O-3 download page <https://h2o.ai/download>`__
-  Java 17 or later in the image (the example uses an Eclipse Temurin 17 base image)

Walkthrough
-----------

**Step 1 - Install and launch Docker**

Follow the `Docker installation instructions <https://docs.docker.com/get-docker/>`__ for your operating system.

**Step 2 - Create a Dockerfile**

Create a folder that contains ``h2o.jar`` and the following ``Dockerfile``:

.. code:: dockerfile

   FROM eclipse-temurin:17-jre

   # Run the engine as a dedicated, unprivileged user.
   RUN groupadd --gid 10001 h2o \
    && useradd --uid 10001 --gid h2o --create-home --shell /usr/sbin/nologin h2o

   COPY --chown=root:root h2o.jar /opt/h2o/h2o.jar

   USER h2o
   WORKDIR /home/h2o

   EXPOSE 54321 54322

   ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=50", "-jar", "/opt/h2o/h2o.jar"]

The image:

-  uses a Java 17 runtime,
-  runs H2O-3 as the unprivileged ``h2o`` user,
-  keeps ``h2o.jar`` owned by ``root`` so the engine can't modify it,
-  declares ports 54321 (control) and 54322 (node-to-node).

**Step 3 - Build the image**

From the folder with the Dockerfile, run:

.. code:: bash

   docker build -t h2o:local .

**Step 4 - Run the container**

Bind the control port to the host's loopback interface so only local clients can reach it, and apply resource and privilege limits:

.. code:: bash

   docker run -d --name h2o \
     -p 127.0.0.1:54321:54321 \
     --memory 4g --cpus 2 \
     --read-only --tmpfs /tmp:rw,exec,size=1g \
     --cap-drop ALL --security-opt no-new-privileges \
     h2o:local

-  ``-p 127.0.0.1:54321:54321`` publishes the control port on the host's loopback interface only. Don't publish it on ``0.0.0.0`` or on a public interface.
-  ``--memory`` and ``--cpus`` set the resources the engine can use. ``-XX:MaxRAMPercentage=50`` sizes the Java heap from the container memory limit.
-  ``--read-only --tmpfs /tmp:rw,exec,size=1g`` makes the root filesystem read-only and gives the engine a writable ``/tmp`` for temporary files. ``exec`` is needed because native libraries such as XGBoost are extracted there. A tmpfs counts against the container's memory limit, so for large jobs mount a volume instead and point ``-ice_root`` at it.
-  ``--cap-drop ALL`` removes Linux capabilities the engine doesn't need, and ``--security-opt no-new-privileges`` prevents the process from gaining privileges after it starts.

To pass H2O-3 options, append them after the image name. For example, to enable hash-file authentication with a ``realm.properties`` file mounted read-only:

.. code:: bash

   docker run -d --name h2o \
     -p 127.0.0.1:54321:54321 \
     --memory 4g --cpus 2 \
     --read-only --tmpfs /tmp:rw,exec,size=1g \
     --cap-drop ALL --security-opt no-new-privileges \
     -v /secure/realm.properties:/etc/h2o/realm.properties:ro \
     h2o:local -hash_login -login_conf /etc/h2o/realm.properties

**Step 5 - Connect from Python or R**

.. code:: python

   import h2o
   h2o.connect(url="http://localhost:54321")

.. code:: r

   library(h2o)
   h2o.connect(ip = "localhost", port = 54321)

If you enabled authentication, pass ``username`` and ``password`` (or an ``auth`` object) to ``h2o.connect``.

**Step 6 - View logs and stop the engine**

.. code:: bash

   docker logs h2o
   docker stop h2o && docker rm h2o

Stop the engine when the work is done. Data is held in memory and is lost when the container stops.

Security
--------

-  **Keep the control port private.** Publish it only on the loopback interface, or not at all when the client runs in the same container network. Other containers on the same Docker network can still reach the port, so run the engine and its client on a dedicated network (``docker network create``).
-  **Run as non-root.** The Dockerfile above creates a dedicated user.
-  **Limit resources and privileges** with ``--memory``, ``--cpus``, ``--read-only``, and ``--cap-drop ALL``.
-  **Enable authentication and TLS** when the client connects over a network that isn't fully trusted. See `Security <../security.html>`__.
-  **Mount data read-only** where possible, and grant the container access only to the data the job needs.
