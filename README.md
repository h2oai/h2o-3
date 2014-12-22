H2O
========

H2O makes Hadoop do math! H2O scales statistics, machine learning and math over BigData. H2O is extensible and users can build blocks using simple math legos in the core. H2O keeps familiar interfaces like R, Excel & JSON so that BigData enthusiasts & experts can explore, munge, model and score datasets using a range of simple to advanced algorithms. Data collection is easy. Decision making is hard. H2O makes it fast and easy to derive insights from your data through faster and better predictive modeling. H2O has a vision of online scoring and modeling in a single platform.

Product Vision for first cut
------------------------------
H2O product, the Analytics Engine will scale Classification and Regression.
- RandomForest, Gradient Boosted Methods (GBM), Generalized Linear Modeling (GLM), Deep Learning and K-Means available over R / REST / JSON-API
- Basic Linear Algebra as building blocks for custom algorithms
- High predictive power of the models
- High speed and scale for modeling and scoring over BigData

Data Sources
- We read and write from/to HDFS, S3, NoSQL, SQL
- We ingest data in CSV format from local and distributed filesystems (nfs)
- A JDBC driver for SQL and DataAdapters for NoSQL datasources is in the roadmap. (v2)

Console provides Adhoc Data Analytics at scale via R-like Parser on BigData
 - Able to pass and evaluate R-like expressions, slicing and filters make this the most powerful web calculator on BigData

Users
--------------------------------
Primary users are Data Analysts looking to wield a powerful tool for Data Modeling in the Real-Time. Microsoft Excel, R, SAS wielding Data Analysts and Statisticians.
Hadoop users with data in HDFS will have a first class citizen for doing Math in Hadoop ecosystem.
Java and Math engineers can extend core functionality by using and extending legos in a simple java that reads like math. See package hex.
Extensibility can also come from writing R expressions that capture your domain.

Design
--------------------------------

We use the best execution framework for the algorithm at hand. For first cut parallel algorithms: Map Reduce over distributed fork/join framework brings fine grain parallelism to distributed algorithms.
Our algorithms are cache oblivious and fit into the heterogeneous datacenter and laptops to bring best performance.
Distributed Arraylets & Data Partitioning to preserve locality.
Move code, not data, not people.

Extensions
---------------------------------

One of our first powerful extension will be a small tool belt of stats and math legos for Fraud Detection. Dealing with Unbalanced Datasets is a key focus for this.
Users will use JSON/REST-api via H2O.R through connects the Analytics Engine into R-IDE/RStudio.

Using H2O Dev Artifacts
--------------------------------
Find H2O Dev on Maven Central via http://search.maven.org/#search%7Cga%7C1%7Cai.h2o

Building H2O Dev
--------------------------------

Getting started with H2O development requires JDK 1.7, Node.js, and Gradle.  We use the Gradle wrapper (called `gradlew`) to ensure an up-to-date local version of Gradle and other dependencies are installed in your development directory.


### Common Setup for all Platforms

##### Step 1. Install required python packages

	(possibly sudo)
	pip install grip


### Setup on Windows

##### Step 1. Install JDK

Install [Java 1.7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html) and add the appropriate directory `C:\Program Files\Java\jdk1.7.0_65\bin` with java.exe to PATH in Environment Variables. Check to make sure the command prompt is detecting the correct Java version by running:

    javac -version


##### Step 2. Install Node.js and npm

Install [Node.js](http://nodejs.org/download/) and add installed directory `C:\Program Files\nodejs` that should include node.exe and npm.cmd to PATH if it isn't already prepended.

##### Step 3. Install R and the required packages

Install [R](http://www.r-project.org/) and add the preferred bin\i386 or bin\x64 directory to your PATH.


Note: Acceptable versions of R are >= 2.13 && <= 3.0.0 && >= 3.1.1.


Install the following R packages: [RCurl](http://cran.r-project.org/package=RCurl), [rjson](http://cran.r-project.org/package=rjson), [statmod](http://cran.r-project.org/package=statmod), [devtools](http://cran.r-project.org/package=devtools), [roxygen2](http://cran.r-project.org/package=roxygen2) and [testthat](http://cran.r-project.org/package=testthat).

    cd Downloads
    R CMD INSTALL RCurl_x.xx-x.x.zip
    R CMD INSTALL rjson_x.x.xx.zip
    R CMD INSTALL statmod_x.x.xx.zip
    R CMD INSTALL devtools_x.x-x.zip
    R CMD INSTALL roxygen2_x.x-x.zip
    R CMD INSTALL testthat_x.x-x.zip

You may alternatively install these packages from within an R session:

> install.packages("RCurl")
> install.packages("rjson")
> install.packages("statmod")
> install.packages("devtools")
> install.packages("roxygen2")
> install.packages("testthat")

##### Step 4. Git Clone [h2o-dev](https://github.com/h2oai/h2o-dev.git)

If you don't already have a Git client, please install one.  The default one can be found here http://git-scm.com/downloads .  Make sure that during the install command prompt support is turned on.

Download and update h2o-dev source codes:

    git clone https://github.com/h2oai/h2o-dev

##### Step 5. Run the top-level gradle build:

    cd h2o-dev
    gradlew.bat build

> If you encounter errors run again with --stacktrace for more instructions on missing dependencies.

### Setup on OS X

If you don't have [Homebrew](http://brew.sh/) install, please consider it.  It makes package management for OS X easy.

##### Step 1. Install JDK

Install [Java 1.7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html). Check to make sure the command prompt is detecting the correct Java version by running:

    javac -version


##### Step 2. Install Node.js and npm

Using Homebrew:

    brew install node

Otherwise install from the [NodeJS website](http://nodejs.org/download/).

##### Step 3. Install R and the required packages

Install [R](http://www.r-project.org/) and add the bin directory to your PATH if not already included.

Install the following R packages: [RCurl](http://cran.r-project.org/package=RCurl), [rjson](http://cran.r-project.org/package=rjson), [statmod](http://cran.r-project.org/package=statmod), [devtools](http://cran.r-project.org/package=devtools), [roxygen2](http://cran.r-project.org/package=roxygen2) and [testthat](http://cran.r-project.org/package=testthat).

    cd Downloads
    R CMD INSTALL RCurl_x.xx-x.x.tgz
    R CMD INSTALL rjson_x.x.xx.tgz
    R CMD INSTALL statmod_x.x.xx.tgz
    R CMD INSTALL devtools_x.x-x.tgz
    R CMD INSTALL roxygen2_x.x-x.tgz
    R CMD INSTALL testthat_x.x-x.tgz

You may alternatively install these packages from within an R session:

> install.packages("RCurl")
> install.packages("rjson")
> install.packages("statmod")
> install.packages("devtools")
> install.packages("roxygen2")
> install.packages("testthat")

##### Step 4. Git Clone [h2o-dev](https://github.com/h2oai/h2o-dev.git)

OS X should have come with Git installed, so just download and update h2o-dev source codes:

    git clone https://github.com/h2oai/h2o-dev

##### Step 5. Run the top-level gradle build:

    cd h2o-dev
    ./gradlew build

> If you encounter errors run again with --stacktrace for more instructions on missing dependencies.

### Setup on Ubuntu 14.04

##### Step 1. Install Node.js and npm

    sudo apt-get install npm
    sudo ln -s /usr/bin/nodejs /usr/bin/node


##### Step 2. Install JDK

Install [Java 1.7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html). Installation instructions can be found here [JDK installation](http://askubuntu.com/questions/56104/how-can-i-install-sun-oracles-proprietary-java-jdk-6-7-8-or-jre). Check to make sure the command prompt is detecting the correct Java version by running:

    javac -version

##### Step 3. Git Clone [h2o-dev](https://github.com/h2oai/h2o-dev.git)

If you don't already have a Git client,

    sudo apt-get install git

Download and update h2o-dev source codes:

    git clone https://github.com/h2oai/h2o-dev

##### Step 4. Run the top-level gradle build:

    cd h2o-dev
    ./gradlew build

> If you encounter errors run again with --stacktrace for more instructions on missing dependencies.

> Make sure that you are not running as root since `bower` will reject such a run

### Setup on Ubuntu 13.10

Step 1. Install Node.js and npm

On Ubuntu 13.10, the default Node.js (v0.10.15) is sufficient, but the default npm (v1.2.18) is too old, so we use a fresh install from the npm website.

    sudo apt-get install node
    sudo ln -s /usr/bin/nodejs /usr/bin/node
    wget http://npmjs.org/install.sh
    sudo apt-get install curl
    sudo sh install.sh

Step 2-4. Follow steps 2-4 for Ubuntu 14.04

### Setting up your preferred build environment

For users of Intellij's IDEA, project files can be generated with:

    ./gradlew idea

For users of Eclipse, project files can be generated with:

    ./gradlew eclipse

Launching H2O on Hadoop from your workspace
-------------------------------------------
Note: This capability is new to h2o-dev and still under development!

### First, choose the version of Hadoop you want to build for

Right now, h2o-dev can only build for one version of Hadoop at a time.  This is specified in **_h2o-dev/build.gradle_** by the line below.

	hadoopVersion = '2.5.0-cdh5.2.0'

The default version is set to CDH5.2 but this also works for HDP2.1 (the example below is launching on hdp2.1).

### Second, do a standard build (if you haven't already)

	(In top-level h2o-dev directory)
	./gradlew build

### Third, do the Hadoop launch

After building, you now have the pieces to run H2O on Hadoop.  Copy the h2o-hadoop.jar and h2o.jar files to an appropriate Hadoop enabled host.  The following command shows an example of running in place, assuming you did your build on a Hadoop enabled host.

	(In top-level h2o-dev directory)
	hadoop jar h2o-hadoop/build/libs/h2o-hadoop.jar water.hadoop.h2odriver -libjars build/h2o.jar -n 3 -mapperXmx 4g -output hdfsOutputDirectory

This example creates a cluster with three nodes where each node has a 4g Java heap.

**_Note: For best results, we recommend you give your cluster enough memory to avoid swapping data to disk. (Of course, this depends on the size of your data!)_**

Output from the above command:

```
Determining driver host interface for mapper->driver callback...
    [Possible callback IP address: 172.16.2.181]
    [Possible callback IP address: 127.0.0.1]
Using mapper->driver callback IP address and port: 172.16.2.181:44948
(You can override these with -driverif and -driverport.)
14/10/26 22:59:07 INFO Configuration.deprecation: mapred.map.child.java.opts is deprecated. Instead, use mapreduce.map.java.opts
Memory Settings:
    mapred.child.java.opts:      -Xms4g -Xmx4g -Dlog4j.defaultInitOverride=true
    mapred.map.child.java.opts:  -Xms4g -Xmx4g -Dlog4j.defaultInitOverride=true
    Extra memory percent:        10
    mapreduce.map.memory.mb:     4505
14/10/26 22:59:07 INFO Configuration.deprecation: mapred.used.genericoptionsparser is deprecated. Instead, use mapreduce.client.genericoptionsparser.used
14/10/26 22:59:07 INFO Configuration.deprecation: mapred.map.tasks.speculative.execution is deprecated. Instead, use mapreduce.map.speculative
14/10/26 22:59:07 INFO Configuration.deprecation: mapred.map.max.attempts is deprecated. Instead, use mapreduce.map.maxattempts
14/10/26 22:59:07 INFO Configuration.deprecation: mapred.job.reuse.jvm.num.tasks is deprecated. Instead, use mapreduce.job.jvm.numtasks
14/10/26 22:59:08 INFO client.RMProxy: Connecting to ResourceManager at mr-0xd7.0xdata.loc/172.16.2.187:8050
14/10/26 22:59:09 INFO mapreduce.JobSubmitter: number of splits:3
14/10/26 22:59:09 INFO mapreduce.JobSubmitter: Submitting tokens for job: job_1413598290344_0035
14/10/26 22:59:09 INFO impl.YarnClientImpl: Submitted application application_1413598290344_0035
14/10/26 22:59:09 INFO mapreduce.Job: The url to track the job: http://mr-0xd7.0xdata.loc:8088/proxy/application_1413598290344_0035/
Job name 'H2O_3581' submitted
JobTracker job ID is 'job_1413598290344_0035'
For YARN users, logs command is 'yarn logs -applicationId application_1413598290344_0035'
Waiting for H2O cluster to come up...
H2O node 172.16.2.184:54321 reports H2O cluster size 1
H2O node 172.16.2.185:54321 reports H2O cluster size 1
H2O node 172.16.2.181:54321 reports H2O cluster size 1
H2O node 172.16.2.181:54321 reports H2O cluster size 3
H2O cluster (3 nodes) is up
(Note: Use the -disown option to exit the driver after cluster formation)
(Press Ctrl-C to kill the cluster)
Blocking until the H2O cluster shuts down...
H2O node 172.16.2.185:54321 reports H2O cluster size 3
H2O node 172.16.2.184:54321 reports H2O cluster size 3
```


Community
---------------------------------
We will breathe & sustain a vibrant community with the focus of taking software engineering approach to data science and empower everyone interested in data to be able to hack data using math and algorithms.
Join us on google groups [h2ostream](https://groups.google.com/forum/#!forum/h2ostream).

Team & Committers

```
SriSatish Ambati
Cliff Click
Tom Kraljevic
Tomas Nykodym
Michal Malohlava
Kevin Normoyle
Spencer Aiello
Anqi Fu
Nidhi Mehta
Arno Candel
Josephine Wang
Amy Wang
Max Schloemer
Ray Peck
Prithvi Prabhu
Patrick Aboyoun
Brandon Hill
Radu Munteanu
Jeff Gambera
Ariel Rao
Viraj Parmar
Kendall Harris
Anna Chavez
Anand Avati
Joel Horwitz
Jessica Lanford
```

Advisors
--------------------------------
Scientific Advisory Council

```
Stephen Boyd
Rob Tibshirani
Trevor Hastie
```

Systems, Data, FileSystems and Hadoop

```
Doug Lea
Chris Pouliot
Dhruba Borthakur
Charles Zedlewski
```

Investors
--------------------------------

```
Jishnu Bhattacharjee, Nexus Venture Partners
Anand Babu Periasamy
Anand Rajaraman
Ash Bhardwaj
Rakesh Mathur
Michael Marks
```
