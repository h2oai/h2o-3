Building H2O
------------

**During the build process, the following error message displays. What
do I need to do to resolve it?**

::

    Error: Missing name at classes.R:19
    In addition: Warning messages:
    1: @S3method is deprecated. Please use @export instead 
    2: @S3method is deprecated. Please use @export instead 
    Execution halted

To build H2O,
`Roxygen2 <https://cran.r-project.org/web/packages/roxygen2/vignettes/roxygen2.html>`__
version 4.1.1 is required.

To update your Roxygen2 version, install the ``versions`` package in R,
then use ``install.versions("roxygen2", "4.1.1")``.

--------------

**Using ``./gradlew build`` doesn't generate a build successfully - is
there anything I can do to troubleshoot?**

Use ``./gradlew clean`` before running ``./gradlew build``.

--------------

**I tried using ``./gradlew build`` after using ``git pull`` to update
my local H2O repo, but now I can't get H2O to build successfully - what
should I do?**

Try using ``./gradlew build -x test`` - the build may be failing tests
if data is not synced correctly.
