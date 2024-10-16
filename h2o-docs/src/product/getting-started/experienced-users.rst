Experienced users 
=================

If you've used previous versions of H2O-3, the following links will help guide you through the process of upgrading H2O-3.

Changes
-------

Change log
~~~~~~~~~~

`This page houses the most recent changes in the latest build of H2O-3 <https://github.com/h2oai/h2o-3/blob/master/Changes.md>`__. It lists new features, improvements,  security updates, documentation improvements, and bug fixes for each release.

API-related changes
~~~~~~~~~~~~~~~~~~~

The `API-related changes <https://docs.h2o.ai/h2o/latest-stable/h2o-docs/api-changes.html>`__ section describes changes made to H2O-3 that can affect backward compatibility.

Developers
----------

If you're looking to use H2O-3 to help you develop your own apps, the following links will provide helpful references.

Gradle
~~~~~~

H2O-3's build is completely managed by Gradle. Any IDEA with Gradle support is sufficient for H2O-3 development. The latest versions of IntelliJ IDEA are thoroughly tested and proven to work well. 

Open the folder with H2O-3 in IntelliJ IDEA and it will automatically recognize that Gradle is requried and will import the project. The Gradle wrapper present in the repository itself may be used manually/directly to build and test if required.

For JUnit tests to pass, you may need multiple H2O-3 nodes. Create a "Run/Debug" configuration:

::

	Type: Application
	Main class: H2OApp
	Use class path of module: h2o-app

After starting multiple "worker" node processes in addition to the JUnit test process, they will cloud up and run the multi-node JUnit tests.

Maven install
~~~~~~~~~~~~~

You can view instructions for using H2O-3 with Maven on the `Downloads page <https://h2o.ai/resources/download/>`__. 

1. Select H2O Open Source Platform or scroll down to H2O.
2. Select the version of H2O-3 you want to install (latest stable or nightly build).
3. Click the Use from Maven tab.

`This page provides information on how to build a version of H2O-3 that generates the correct IDE files <https://github.com/h2oai/h2o-3/blob/master/build.gradle>`__ for your Maven installation.

Developer resources
~~~~~~~~~~~~~~~~~~~

Documentation
'''''''''''''

See the detailed `instructions on how to build and launch H2O-3 <https://github.com/h2oai/h2o-3#4-building-h2o-3>`__, including how to clone the repository, how to pull from the repository, and how to install required dependencies.

Droplet project templates
^^^^^^^^^^^^^^^^^^^^^^^^^

`This page provides template information <https://github.com/h2oai/h2o-droplets>`__ for projects created in Java, Scala, or Sparkling Water.

Blogs
'''''

Learn more about performance characteristics when implementing new algorithms in this `KV Store guide blog <https://www.h2o.ai/blog/kv-store-memory-analytics-part-2-2/>`__.

This `blog post by Cliff <https://www.h2o.ai/blog/hacking-algorithms-in-h2o-with-cliff/>`__ walks you through building a new algorithm, using K-Means, Quantiles, and Grep as examples.

Join the H2O community
----------------------

`Join our community support and outreach <https://h2o.ai/community/>`__ by accessing self-paced courses, scoping out meetups, and interacting with other users and our team.

Contributing code
~~~~~~~~~~~~~~~~~

If you're interested in contributing code to H2O-3, we appreciate your assistance! See `how to contribute to H2O-3 <https://github.com/h2oai/h2o-3/blob/master/CONTRIBUTING.md>`__. This document describes how to access our list of issues, or suggested tasks for contributors, and how to contact us.