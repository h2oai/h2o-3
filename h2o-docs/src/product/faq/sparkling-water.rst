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

**How can I save and load a K-means model using Sparkling Water?**

The following example code defines the save and load functions
explicitly.

::

    import water._
    import _root_.hex._
    import java.net.URI
    import water.serial.ObjectTreeBinarySerializer
    // Save H2O model (as binary)
    def exportH2OModel(model : Model[_,_,_], destination: URI): URI = {
      val modelKey = model._key.asInstanceOf[Key[_ <: Keyed[_ <: Keyed[_ <: AnyRef]]]]
      val keysToExport = model.getPublishedKeys()
      // Prepend model key
      keysToExport.add(0, modelKey)

      new ObjectTreeBinarySerializer().save(keysToExport, destination)
      destination
    }

    // Get model from H2O DKV and Save to disk
    val gbmModel: _root_.hex.tree.gbm.GBMModel = DKV.getGet("model")
    exportH2OModel(gbmModel, new File("../h2omodel.bin").toURI)



    def loadH2OModel[M <: Model[_, _, _]](source: URI) : M = {
        val l = new ObjectTreeBinarySerializer().load(source)
        l.get(0).get().asInstanceOf[M]
      }
    // Load H2O model
    def loadH2OModel[M <: Model[_, _, _]](source: URI) : M = {
        val l = new ObjectTreeBinarySerializer().load(source)
        l.get(0).get().asInstanceOf[M]
      }
      
    // Load model
    val h2oModel: Model[_, _, _] = loadH2OModel(new File("../h2omodel.bin").toURI)

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

-  Use
   ``$SPARK_HOME/bin/spark-shell --packages ai.h2o:sparkling-water-core_2.10:1.3.3``

or

-  ``bin/sparkling-shell``

The software distribution provides example scripts in the
``examples/scripts`` directory:

``bin/sparkling-shell -i examples/scripts/chicagoCrimeSmallShell.script.scala``

For either method, initialize H2O as shown in the following example:

::

    import org.apache.spark.h2o._
    val h2oContext = new H2OContext(sc).start()

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

	$SPARK_HOME/bin/spark-submit ChicagoCrimeAppSmall --packages ai.h2o:sparkling-water-core_2.10:1.3.3 --packages ai.h2o:sparkling-water-examples_2.10:1.3.3

--------------

**How do I use Sparkling Water with Databricks cloud?**

Sparkling Water compatibility with Databricks cloud is still in
development.

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

--------------

**Occasionally I receive an "out of memory" error from Sparkling Water. Is there a method based on the trees/depth/cross-validations that is used to determine how much memory is needed to store the model?**

We normally suggest 3-4 times the size of the dataset for the amount of memory required. It's difficult to calculate the exact memory footprint for each case because running with a different solver or with different parameters can change the memory footprint drastically. In GLM for example, there's an internal heuristic for checking the estimated memory needed:

`https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/main/java/hex/glm/GLM.java#L182 <https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/main/java/hex/glm/GLM.java#L182>`__

In this particular case, if you use IRLSM or Coordinate Descent, the memory footprint is roughly in bytes:

  (number of predictors expanded)^2 * (# of cores on one machine) * 4 

And if you run cross validation with, for example, two-fold xval, then the memory footprint would be double this number; three-fold would be triple, etc.

For GBM and Random Forest, another heuristic is run by checking how much memory is needed to hold the trees. Additionally, the number of histograms that are calculated per predictor also matters. 

Depending on the user-specified predictors, max_depth per tree, the number of trees, nbins per histogram, and the number of classes (binomial vs multinomial), memory consumption will vary:

- Holding trees: 2^max_depth * nclasses * ntrees * 10 (or the avg bytes/element)
- Computing histograms: (num_predictors) * nbins * 3 (histogram/leaf) * (2^max_depth) * nclasses * 8

--------------

**Also, is there a way to clear everything from H2O (including H2OFrames/Models)?**

You can open Flow and select individual items to delete from H2O, or you can run the following to remove everything from H2O:

::

    import water.api.RemoveAllHandler
    new RemoveAllHandler().remove(3,new RemoveAllV3())

