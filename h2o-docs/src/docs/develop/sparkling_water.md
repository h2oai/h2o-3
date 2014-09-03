# Developing with Sparkling Water (H2O <-> Apache Spark integration)

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



*NOTE: if gradle install hangs it may be because some other process, such as IntelliJ, is holding the lock on the artifact cache.  If so, stop that process and retry gradle install.*

Create a git clone of the Perrier repository.

    cd ..
    git clone https://github.com/0xdata/perrier.git
    cd perrier
    export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=512M -XX:ReservedCodeCacheSize=512m"
    mvn -DskipTests clean install    # install all the artifacts of all the projects to the Maven cache
    sbt/sbt clean assembly           # build fat jar using the artifacts
    
If you have the Maven plugin installed and enabled in IntelliJ IDEA you can open the project there by doing File ->  Open and choosing perrier/pom.xml.

