# Developing with Sparkling Water (H2O <-> Apache Spark integration)

## Setting Up Your Development Environment

These instructions assume you are using Linux, MacOSX, or Cygwin (on Windows).

Ensure that you have the Java 7 SDK and Maven installed:
http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html
http://maven.apache.org

Create a git clone of the h2o-dev repository and build the software:

    git clone https://github.com/0xdata/h2o-dev.git
    cd h2o-dev
    ./gradlew install # will build h2o and push it to your local Maven artifact cache
    
To do this for Java7 only, you can do this instead, which is a bit faster:

    ./gradlew -Pdisable.java6bytecode.gen install



>If *gradle install* hangs it may be because some other process, such as IntelliJ, is holding the lock on the artifact cache.  If so, stop that process and retry *gradle install*.

Create a git clone of the Perrier repository.

    cd ..
    git clone https://github.com/0xdata/perrier.git
    cd perrier
    export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=512M -XX:ReservedCodeCacheSize=512m"
    mvn -DskipTests clean install    # install all the artifacts of all the projects to the Maven cache
    sbt/sbt clean assembly           # build fat jar using the artifacts
    
If you have the Maven plugin installed and enabled in IntelliJ IDEA you can open the project there by doing *File ->  Open* and choosing *perrier/pom.xml*.

If you want to build the Perrier project inside of IntelliJ without rebuilding all the base Spark modules you can disable them in the Maven tab: select all the folders except for *Profiles*, the three whose name contains *H2O*, and *Spark Project Parent POM (root)*, right click, and select *Ignore Projects*.

>If IntelliJ can't find dependencies (e.g., *spark-core*) find the failing dependant modules in the Project view (e.g., *h20-perrier*), right click -> *Maven* -> *Reimport* and then *Build* -> *Rebuild Project*.

## Running an Example

In IntelliJ locate the ProstateDemo class.  Right click and run.  This will launch a Spark instance in local mode containing an embedded H2O instance, and will run a Spark application which loads data into H2O, converts it to a Spark RDD, filters the data using a Spark SQL query, moves the result to H2O, and runs a KMeans clustering algorithm.

In IntelliJ locate the DeepLearningSuite class.  Right click and run.  This will launch a Spark instance in local mode containing an embedded H2O instance, and will run a Spark application which generates test data in a Spark RDD, moves that data into H2O, and launches DeepLearning which creates a binary classification model.  This model is used to generate predictions on test data from Spark, which is then pushed back to a Spark RDD where it is validated using Spark's standard technique.

## Running an Example with a Standalone Cluster

### Launch a Standalone Spark Cluster

    cd perrier/sbin
    # the following commands are from sbin/launch-spark-cloud.sh:
    export SPARK_PRINT_LAUNCH_COMMAND=1
    export SPARK_MASTER_IP="localhost"
    export SPARK_MASTER_PORT="7077"
    export SPARK_WORKER_PORT="7087"
    export SPARK_WORKER_INSTANCES=2
    export MASTER="spark://$SPARK_MASTER_IP:$SPARK_MASTER_PORT"
    ./start-master.sh 
    ./start-slave.sh 1 $MASTER
    ./start-slave.sh 2 $MASTER

### Assemble the Application for Spark. . .
    
    cd perrier/h2o-examples
    # the following commands are from h2o-examples/make-package.sh:
    mvn package -DXX:MaxPermSize=128m -DskipTests -Dclean.skip -Dmaven.test.skip=true -Dmaven.javadoc.skip=true -Dscalastyle.skip=true -Dmaven.scaladoc.skip=true -Dskip=true
    
    
### . . .and Submit It to the Cluster

    cd perrier/h2o-examples
    # the following command is from h2o-examples/run-example.sh:
    ( cd ../; bin/spark-submit --verbose --master "spark://$SPARK_MASTER_IP:$SPARK_MASTER_PORT" --class org.apache.examples.h2o.ProstateDemo h2o-examples/target/shaded.jar )
    
    
