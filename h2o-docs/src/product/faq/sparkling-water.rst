Sparkling Water
---------------

**What is Sparkling Water?**

Sparkling Water allows users to combine the fast, scalable machine
learning algorithms of H2O with the capabilities of Spark. With
Sparkling Water, users can drive computation from Scala/R/Python and
utilize the H2O Flow UI, providing an ideal machine learning platform
for application developers.

--------------

**What are the advantages of using Sparkling Water compared with H2O?**

Sparkling Water contains the same features and functionality as H2O but
provides a way to use H2O with `Spark <http://spark.apache.org/>`__, a
large-scale cluster framework.

Sparkling Water is ideal for H2O users who need to manage large clusters
for their data processing needs and want to transfer data from Spark to
H2O (or vice versa).

There is also a Python interface available to enable access to Sparkling
Water directly from PySpark.

--------------

**How do I filter an H2OFrame using Sparkling Water?**

Filtering columns is easy: just remove the unnecessary columns or create
a new H2OFrame from the columns you want to include
(``Frame(String[] names, Vec[] vec)``), then make the H2OFrame wrapper
around it (``new H2OFrame(frame)``).

Filtering rows is a little bit harder. There are two ways:

-  Create an additional binary vector holding ``1/0`` for the ``in/out``
   sample (make sure to take this additional vector into account in your
   computations). This solution is quite cheap, since you do not
   duplicate data - just create a simple vector in a data walk.

or

-  Create a new frame with the filtered rows. This is a harder task,
   since you have to copy data. For reference, look at the #deepSlice
   call on Frame (``H2OFrame``)

--------------

**How do I save and load models using Sparkling Water?**

Models can be saved and loaded in Sparkling Water using the ``water.support.ModelSerializationSupport class``. For example:

::

  #Test model
  val model = h2oModel

  #Export model on disk
  ModelSerializationSupport.exportModel(model, destinationURI)

  # Export the POJO model
  ModelSerializationSupport.exportPOJOModel(model, desinationURI)

  #Load the model from disk
  val loadedModel = ModelSerializationSupport.loadModel(pathToModel)

  
Note that you can also specify type of model to be loaded:

::

  val loadedModel = ModelSerializationSupport.loadMode[TYPE_OF_MODEL]l(pathToModel)

--------------

**How do I inspect H2O using Flow while a droplet is running?**

If your droplet execution time is very short, add a simple sleep
statement to your code:

``Thread.sleep(...)``

--------------

**How do I change the memory size of the executors in a droplet?**

There are two ways to do this:

-  Change your default Spark setup in
   ``$SPARK_HOME/conf/spark-defaults.conf``

or

-  Pass ``--conf`` via spark-submit when you launch your droplet (e.g.,

::

	$SPARK_HOME/bin/spark-submit --conf spark.executor.memory=4g --master $MASTER --class org.my.Droplet $TOPDIR/assembly/build/libs/droplet.jar

--------------

**I received the following error while running Sparkling Water using
multiple nodes, but not when using a single node - what should I do?**

::

    onExCompletion for water.parser.ParseDataset$MultiFileParseTask@31cd4150
    water.DException$DistributedException: from /10.23.36.177:54321; by class water.parser.ParseDataset$MultiFileParseTask; class water.DException$DistributedException: from /10.23.36.177:54325; by class water.parser.ParseDataset$MultiFileParseTask; class water.DException$DistributedException: from /10.23.36.178:54325; by class water.parser.ParseDataset$MultiFileParseTask$DistributedParse; class java.lang.NullPointerException: null
        at water.persist.PersistManager.load(PersistManager.java:141)
        at water.Value.loadPersist(Value.java:226)
        at water.Value.memOrLoad(Value.java:123)
        at water.Value.get(Value.java:137)
        at water.fvec.Vec.chunkForChunkIdx(Vec.java:794)
        at water.fvec.ByteVec.chunkForChunkIdx(ByteVec.java:18)
        at water.fvec.ByteVec.chunkForChunkIdx(ByteVec.java:14)
        at water.MRTask.compute2(MRTask.java:426)
        at water.MRTask.compute2(MRTask.java:398)

This error output displays if the input file is not present on all
nodes. Because of the way that Sparkling Water distributes data, the
input file is required on all nodes (including remote), not just the
primary node. Make sure there is a copy of the input file on all the
nodes, then try again.

--------------

**Are there any drawbacks to using Sparkling Water compared to
standalone H2O?**

The version of H2O embedded in Sparkling Water is the same as the
standalone version.

--------------

**How do I use Sparkling Water from the Spark shell?**

There are two methods:

Use
   
   ``$SPARK_HOME/bin/spark-shell --packages ai.h2o:sparkling-water-core_2.11:2.1.12``

or

  ``bin/sparkling-shell``

The software distribution provides example scripts in the
``examples/scripts`` directory:

``bin/sparkling-shell -i examples/scripts/chicagoCrimeSmallShell.script.scala``

For either method, initialize H2O as shown in the following example:

::

    import org.apache.spark.h2o._
    val h2oContext = H2OContext.getOrCreate(spark)
    import h2oContext._

After successfully launching H2O, the following output displays:

::

    Sparkling Water Context:
     * number of executors: 3
     * list of used executors:
      (executorId, host, port)
      ------------------------
      (1,Michals-MBP.0xdata.loc,54325)
      (0,Michals-MBP.0xdata.loc,54321)
      (2,Michals-MBP.0xdata.loc,54323)
      ------------------------

      Open H2O Flow in browser: http://172.16.2.223:54327 (CMD + click in Mac OSX)
      

--------------

**How do I use H2O with Spark Submit?**

Spark Submit is for submitting self-contained applications. For more
information, refer to the `Spark
documentation <https://spark.apache.org/docs/latest/quick-start.html#self-contained-applications>`__.

First, initialize H2O:

::

    import org.apache.spark.h2o._
    val h2oContext = new H2OContext(sc).start()

The Sparkling Water distribution provides several examples of
self-contained applications built with Sparkling Water. To run the
examples:

``bin/run-example.sh ChicagoCrimeAppSmall``

The "magic" behind ``run-example.sh`` is a regular Spark Submit:

::

	$SPARK_HOME/bin/spark-submit ChicagoCrimeAppSmall --packages ai.h2o:sparkling-water-core_2.11:2.1.12 --packages ai.h2o:sparkling-water-examples_2.11:2.1.12

--------------

**How do I use Sparkling Water with Databricks?**

Refer to `Using H2O Sparking Water with Databricks <../cloud-integration/databricks.html>`__ for information on how to use Sparkling Water with Databricks.

--------------

**How do I develop applications with Sparkling Water?**

For a regular Spark application (a self-contained application as
described in the `Spark
documentation <https://spark.apache.org/docs/latest/quick-start.html#self-contained-applications>`__),
the app needs to initialize ``H2OServices`` via ``H2OContext``:

::

    import org.apache.spark.h2o._
    val h2oContext = new H2OContext(sc).start()

For more information, refer to the `Sparkling Water development
documentation <https://github.com/h2oai/sparkling-water/blob/master/DEVEL.md>`__.

--------------

**How do I connect to Sparkling Water from R or Python?**

After starting ``H2OServices`` by starting ``H2OContext``, point your
client to the IP address and port number specified in ``H2OContext``.
