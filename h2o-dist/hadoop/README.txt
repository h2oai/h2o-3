
RUNNING H2O-DEV ON HADOOP
=========================

This is the README file that is packaged with the pre-built h2o-dev
zip distribution.  If you are reading this then you have downloaded
and unpacked the zip file.

For more information, see the online help:

    1.  Visit http://h2o-release.s3.amazonaws.com/h2o-dev/SUBST_BRANCH_NAME/SUBST_BUILD_NUMBER/index.html
    2.  Click on the "Install on Hadoop" tab



EXAMPLE OF HOW TO START AN H2O CLUSTER ON HADOOP
------------------------------------------------

$ hadoop jar h2odriver.jar -nodes 1 -mapperXmx 6g -output hdfsOutputDirName


    -nodes       The number of H2O nodes to start in the H2O cluster.
                 Starting more than one node per host is not recommended.

    -mapperXmx   The amount of Java memory each node gets.
                 Be sure to choose a large enough value for your data size.
                 In this example, 6g is 6 Gigabytes.

    -output      A unique directory in hdfs where some intermediate files get
                 stored.  These are not typically useful for H2O, but part of
                 the standard Hadoop ToolRunner framework that H2O builds on.
                 This directory *must not* exist before starting H2O.



ADDITONAL COMMAND-LINE HELP
---------------------------

To see a list of additional options, use the -help flag as follows:

$ hadoop jar h2odriver.jar -help



EXAMPLE OF HOW TO START A STANDALONE H2O IN HDFS CLIENT MODE
------------------------------------------------------------

$ java -cp h2odriver.jar water.H2OApp


    Note:        This starts a single H2O node with HDFS access capability.
                 If you have kerberos enabled you must use the 'hadoop jar'
                 method to access HDFS.

