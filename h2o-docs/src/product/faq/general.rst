General
-------

**How do I score using an exported JSON model?**

Since JSON is just a representation format, it cannot be directly
executed, so a JSON export can't be used for scoring. However, you can
score by:

-  including the POJO in your execution stream and handing it
   observations one at a time

or

-  handing your data in bulk to an H2O cluster, which will score using
   high throughput parallel and distributed bulk scoring.

--------------

**How do I score using an exported POJO?**

The generated POJO can be used indepedently of a H2O cluster. First use
``curl`` to send the h2o-genmodel.jar file and the java code for model
to the server. The following is an example; the ip address and model
names will need to be changed.

::

    mkdir tmpdir
    cd tmpdir
    curl http://127.0.0.1:54321/3/h2o-genmodel.jar > h2o-genmodel.jar
    curl http://127.0.0.1:54321/3/Models.java/gbm_model > gbm_model.java

To score a simple .CSV file, download the
`PredictCsv.java <https://github.com/h2oai/h2o-3/blob/master/h2o-genmodel/src/main/java/hex/genmodel/tools/PredictCsv.java>`_ file and compile it with the POJO. Make a subdirectory for the compilation (this is useful if you have multiple models to score on).

::

    wget https://raw.githubusercontent.com/h2oai/h2o-3/master/h2o-genmodel/src/main/java/hex/genmodel/tools/PredictCsv.java
    javac -cp h2o-genmodel.jar -J-Xmx2g -J-XX:MaxPermSize=128m PredictCSV.java gbm_model.java -d gbm_model_dir

Specify the following: 

- the classpath using ``-cp`` 
- the model name (or class) using ``--model`` 
- the csv file you want to score using ``--input`` 
- the location for the predictions using ``--output``.

You must match the table column names to the order specified in the
POJO. The output file will be in a .hex format, which is a lossless text
representation of floating point numbers. Both R and Java will be able
to read the hex strings as numerics.

::

    java -ea -cp h2o-genmodel.jar:gbm_model_dir -Xmx4g -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=256m hex.genmodel.tools.PredictCsv --header --model gbm_model --input input.csv --output output.csv

--------------

**How do I predict using multiple response variables?**

Currently, H2O does not support multiple response variables. To predict
different response variables, build multiple models.

--------------

**How do I kill any running instances of H2O?**

In Terminal, enter ``ps -efww | grep h2o``, then kill any running PIDs.
You can also find the running instance in Terminal and press **Ctrl +
C** on your keyboard. To confirm no H2O sessions are still running, go
to ``http://localhost:54321`` and verify that the H2O web UI does not
display.

--------------

**Why is H2O not launching from the command line?**

::

    $ java -jar h2o.jar &
    % Exception in thread "main" java.lang.ExceptionInInitializerError
    at java.lang.Class.initializeClass(libgcj.so.10)
    at water.Boot.getMD5(Boot.java:73)
    at water.Boot.<init>(Boot.java:114)
    at water.Boot.<clinit>(Boot.java:57)
    at java.lang.Class.initializeClass(libgcj.so.10)
    Caused by: java.lang.IllegalArgumentException
    at java.util.regex.Pattern.compile(libgcj.so.10)
    at water.util.Utils.<clinit>(Utils.java:1286)
    at java.lang.Class.initializeClass(libgcj.so.10)
    ...4 more

The only prerequisite for running H2O is a compatible version of Java.
We recommend Oracle's `Java
1.7 <http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html>`__.

--------------

**Why did I receive the following error when I tried to launch H2O?**

::

    [root@sandbox h2o-dev-0.3.0.1188-hdp2.2]hadoop jar h2odriver.jar -nodes 2 -mapperXmx 1g -output hdfsOutputDirName
    Determining driver host interface for mapper->driver callback...
       [Possible callback IP address: 10.0.2.15]
       [Possible callback IP address: 127.0.0.1]
    Using mapper->driver callback IP address and port: 10.0.2.15:41188
    (You can override these with -driverif and -driverport.)
    Memory Settings:
       mapreduce.map.java.opts:     -Xms1g -Xmx1g -Dlog4j.defaultInitOverride=true
       Extra memory percent:        10
       mapreduce.map.memory.mb:     1126
    15/05/08 02:33:40 INFO impl.TimelineClientImpl: Timeline service address: http://sandbox.hortonworks.com:8188/ws/v1/timeline/
    15/05/08 02:33:41 INFO client.RMProxy: Connecting to ResourceManager at sandbox.hortonworks.com/10.0.2.15:8050
    15/05/08 02:33:47 INFO mapreduce.JobSubmitter: number of splits:2
    15/05/08 02:33:48 INFO mapreduce.JobSubmitter: Submitting tokens for job: job_1431052132967_0001
    15/05/08 02:33:51 INFO impl.YarnClientImpl: Submitted application application_1431052132967_0001
    15/05/08 02:33:51 INFO mapreduce.Job: The url to track the job: http://sandbox.hortonworks.com:8088/proxy/application_1431052132967_0001/
    Job name 'H2O_3889' submitted
    JobTracker job ID is 'job_1431052132967_0001'
    For YARN users, logs command is 'yarn logs -applicationId application_1431052132967_0001'
    Waiting for H2O cluster to come up...
    H2O node 10.0.2.15:54321 requested flatfile
    ERROR: Timed out waiting for H2O cluster to come up (120 seconds)
    ERROR: (Try specifying the -timeout option to increase the waiting time limit)
    15/05/08 02:35:59 INFO impl.TimelineClientImpl: Timeline service address: http://sandbox.hortonworks.com:8188/ws/v1/timeline/
    15/05/08 02:35:59 INFO client.RMProxy: Connecting to ResourceManager at sandbox.hortonworks.com/10.0.2.15:8050

    ----- YARN cluster metrics -----
    Number of YARN worker nodes: 1

    ----- Nodes -----
    Node: http://sandbox.hortonworks.com:8042 Rack: /default-rack, RUNNING, 1 containers used, 0.2 / 2.2 GB used, 1 / 8 vcores used

    ----- Queues -----
    Queue name:            default
       Queue state:       RUNNING
       Current capacity:  0.11
       Capacity:          1.00
       Maximum capacity:  1.00
       Application count: 1
       ----- Applications in this queue -----
       Application ID:                  application_1431052132967_0001 (H2O_3889)
           Started:                     root (Fri May 08 02:33:50 UTC 2015)
           Application state:           FINISHED
           Tracking URL:                http://sandbox.hortonworks.com:8088/proxy/application_1431052132967_0001/jobhistory/job/job_1431052132967_0001
           Queue name:                  default
           Used/Reserved containers:    1 / 0
           Needed/Used/Reserved memory: 0.2 GB / 0.2 GB / 0.0 GB
           Needed/Used/Reserved vcores: 1 / 1 / 0

    Queue 'default' approximate utilization: 0.2 / 2.2 GB used, 1 / 8 vcores used

    ----------------------------------------------------------------------

    ERROR:   Job memory request (2.2 GB) exceeds available YARN cluster memory (2.2 GB)
    WARNING: Job memory request (2.2 GB) exceeds queue available memory capacity (2.0 GB)
    ERROR:   Only 1 out of the requested 2 worker containers were started due to YARN cluster resource limitations

    ----------------------------------------------------------------------
    Attempting to clean up hadoop job...
    15/05/08 02:35:59 INFO impl.YarnClientImpl: Killed application application_1431052132967_0001
    Killed.
    [root@sandbox h2o-dev-0.3.0.1188-hdp2.2]#

The H2O launch failed because more memory was requested than was
available. Make sure you are not trying to specify more memory in the
launch parameters than you have available.

--------------

**How does the architecture of H2O work?**

This
`PDF <https://github.com/h2oai/h2o-meetups/blob/master/2014_11_18_H2O_in_Big_Data_Environments/H2OinBigDataEnvironments.pdf>`__
includes diagrams and slides depicting how H2O works in big data
environments.

--------------

**How does ``importFiles()`` work in H2O?**

``importFiles()`` gets the basic information for the file and then
returns a key representing that file. This key is used during parsing to
read in the file and to save space so that the file isn't loaded every
time; instead, it is loaded into H2O then referenced using the key. For
files hosted online, H2O verifies the destination is valid, creates a
vec that loads the file when necessary, and returns a key.

--------------

**Does H2O support GPUs?**

H2O supports GPUs as part of our Deep Water offering. Refer to the `Deep Water <https://www.h2o.ai/deep-water/>`__ product section of the H2O.ai site for more information. Users with an AWS account can also follow the `"H2O + TensorFlow on AWS GPU" <https://blog.h2o.ai/2016/07/h2o-tensorflow-on-aws-gpu/>`__ tutorial. 

--------------

**How can I continue working on a model in H2O after restarting?**

There are a number of ways you can save your model in H2O:

-  In the web UI, click the **Flow** menu then click **Save Flow**. Your
   flow is saved to the *Flows* tab in the **Help** sidebar on the
   right.
-  In the web UI, click the **Flow** menu then click **Download this
   Flow...**. Depending on your browser and configuration, your flow is
   saved to the "Downloads" folder (by default) or to the location you
   specify in the pop-up **Save As** window if it appears.
-  (For DRF, GBM, and DL models only): Use model checkpointing to resume
   training a model. Copy the ``model_id`` number from a built model and
   paste it into the *checkpoint* field in the ``buildModel`` cell.

--------------

**How can I find out more about H2O's real-time, nano-fast scoring
engine?**

H2O's scoring engine uses a Plain Old Java Object (POJO). The POJO code
runs quickly but is single-threaded. It is intended for embedding into
lightweight real-time environments.

All the work is done by the call to the appropriate predict method.
There is no involvement from H2O in this case.

To compare multiple models simultaneously, use the POJO to call the
models using multiple threads. For more information on using POJOs,
refer to the `POJO Quick Start Guide <pojo-quick-start.html>`__
and `POJO Java Documentation <../h2o-genmodel/javadoc/index.html>`__

In-H2O scoring is triggered on an existing H2O cluster, typically using
a REST API call. H2O evaluates the predictions in a parallel and
distributed fashion for this case. The predictions are stored into a new
Frame and can be written out using ``h2o.exportFile()``, for example.

--------------

**I am writing an academic research paper and I would like to cite H2O
in my bibliography - how should I do that?**

To cite our software:

-  The H2O.ai Team. (2015) *h2o: R Interface for H2O*. R package version
   3.1.0.99999. http://www.h2o.ai.

-  The H2O.ai Team. (2015) *h2o: h2o: Python Interface for H2O*. Python
   package version 3.1.0.99999. http://www.h2o.ai.

-  The H2O.ai Team. (2015) *H2O: Scalable Machine Learning*. Version 3.1.0.99999. http://www.h2o.ai.

To cite one of our booklets:

-  Nykodym, T., Hussami, N., Kraljevic, T.,Rao, A., and Wang, A. (Sept.
   2015). *Generalized Linear Modeling with H2O.*
   http://h2o.ai/resources.

-  Candel, A., LeDell, E., Parmar, V., and Arora, A. (Sept. 2015). *Deep
   Learning with H2O.* http://h2o.ai/resources.

-  Click, C., Malohlava, M., Parmar, V., and Roark, H. (Sept. 2015).
   *Gradient Boosted Models with H2O.* http://h2o.ai/resources.

-  Aiello, S., Eckstrand, E., Fu, A., Landry, M., and Aboyoun, P. (Sept.
   2015) *Fast Scalable R with H2O.* http://h2o.ai/resources.

-  Aiello, S., Click, C., Roark, H. and Rehak, L. (Sept. 2015) *Machine
   Learning with Python and H2O* http://h2o.ai/resources.

-  Malohlava, M., and Tellez, A. (Sept. 2015) *Machine Learning with
   Sparkling Water: H2O + Spark* http://h2o.ai/resources.

If you are using Bibtex:

::


    @Manual{h2o_GLM_booklet,
        title = {Generalized Linear Modeling with H2O},
        author = {Nykodym, T. and Hussami, N. and Kraljevic, T. and Rao, A. and Wang, A.},
        year = {2015},
        month = {September},
        url = {http://h2o.ai/resources},
    }

    @Manual{h2o_DL_booklet,
        title = {Deep Learning with H2O},
        author = {Candel, A. and LeDell, E. and Arora, A. and Parmar, V.},
        year = {2015},
        month = {September},
        url = {http://h2o.ai/resources},
    }

    @Manual{h2o_GBM_booklet,
        title = {Gradient Boosted Models},
        author = {Click, C. and Lanford, J. and Malohlava, M. and Parmar, V. and Roark, H.},
        year = {2015},
        month = {September},
        url = {http://h2o.ai/resources},
    }

    @Manual{h2o_R_booklet,
        title = {Fast Scalable R with H2O},
        author = {Aiello, S. and Eckstrand, E. and Fu, A. and Landry, M. and Aboyoun, P. },
        year = {2015},
        month = {September},
        url = {http://h2o.ai/resources},
    }

    @Manual{h2o_R_package,
        title = {h2o: R Interface for H2O},
        author = {The H2O.ai team},
        year = {2015},
        note = {R package version 3.1.0.99999},
        url = {http://www.h2o.ai},
    }


    @Manual{h2o_Python_module,
        title = {h2o: Python Interface for H2O},
        author = {The H2O.ai team},
        year = {2015},
        note = {Python package version 3.1.0.99999},
        url = {http://www.h2o.ai},
    }


    @Manual{h2o_Java_software,
        title = {H2O: Scalable Machine Learning},
        author = {The H2O.ai team},
        year = {2015},
        note = {version 3.1.0.99999},
        url = {http://www.h2o.ai},
    }

--------------

**What are these RTMP and py\_ temporary Frames? Why are they the same
size as my original data?**

No data is copied. H2O does a classic copy-on-write optimization. That
Frame you see - it's nothing more than a thin wrapper over an internal
list of columns; the columns are shared to avoid the copying.

The RTMP's now need to be entirely managed by the H2O wrapper - because
indeed they are using shared state under the hood. If you delete one,
you probably delete parts of others. Instead, temp management should be
automatic and "good" - as in: it's a bug if you need to delete a temp
manually, or if passing around Frames, or adding or removing columns
turns into large data copies.

R's GC is now used to remove unused R temps, and when the last use of a
shared column goes away, then the H2O wrapper will tell the H2O cluster
to remove that no longer needed column.

In other words: Don't delete RTMPs, they'll disappear at the next R GC.
Don't worry about copies (they aren't getting made). Do Nothing and All
Is Well.
