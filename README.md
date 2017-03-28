# H2O

[![Join the chat at https://gitter.im/h2oai/h2o-3](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/h2oai/h2o-3?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

H2O scales statistics, machine learning, and math over Big Data. 

H2O uses familiar interfaces like R, Python, Scala, the Flow notebook graphical interface, Excel, & JSON so that Big Data enthusiasts & experts can explore, munge, model, and score datasets using a range of algorithms including advanced ones like Deep Learning. H2O is extensible so that developers can add data transformations and model algorithms of their choice and access them through all of those clients.

Data collection is easy. Decision making is hard. H2O makes it fast and easy to derive insights from your data through faster and better predictive modeling. H2O allows online scoring and modeling in a single platform.

* [Downloading H2O-3](#Downloading)
* [Open Source Resources](#Resources)
    * [Issue Tracking and Feature Requests](#IssueTracking) 
    * [List of Open Source Resources](#OpenSourceResources)
* [Using H2O-3 Code Artifacts (libraries)](#Artifacts)
* [Building H2O-3](#Building)
* [Launching H2O after Building](#Launching)
* [Building H2O on Hadoop](#BuildingHadoop)
* [Sparkling Water](#Sparkling)
* [Documentation](#Documentation)
* [Citing H2O](#Citing)
* [Community](#Community) / [Advisors](#Advisors) / [Investors](#Investors)

<a name="Downloading"></a>
## 1. Downloading H2O-3

While most of this README is written for developers who do their own builds, most H2O users just download and use a pre-built version.  If that's you, just follow these steps:

1.  Point to <http://h2o.ai>
2.  Click on Download
3.  Click on Download H2O-3
4.  Choose a platform
5.  Follow the instructions on the download page

For documentation, please visit <http://docs.h2o.ai>.

<a name="Resources"></a>
## 2. Open Source Resources

Most people interact with three primary open source resources:  **GitHub** (which you've already found), **JIRA** (for issue tracking), and **h2ostream** (a community discussion forum).

<a name="IssueTracking"></a>
### 2.1 Issue Tracking and Feature Requests

> (Note: There is only one issue tracking system for the project.  GitHub issues are not enabled; you must use JIRA.)

You can browse and create new issues in our open source **JIRA**:  <http://jira.h2o.ai>

*  You can **browse** and search for **issues** without logging in to JIRA:
    1.  Click the `Issues` menu
    1.  Click `Search for issues`
*  To **create** an **issue** (either a bug or a feature request), please create yourself an account first:
    1.  Click the `Log In` button on the top right of the screen
    1.  Click `Create an acccount` near the bottom of the login box
    1.  Once you have created an account and logged in, use the `Create` button on the menu to create an issue
    1.  Create H2O-3 issues in the [PUBDEV](https://0xdata.atlassian.net/projects/PUBDEV/issues) project
*	You can also vote for feature requests and/or other issues. Voting can help H2O prioritize the features that are included in each release. 
	1. Go to the [H2O JIRA page](https://0xdata.atlassian.net/).
	2. Click **Log In** to either log in or create an account if you do not already have one.
	3. Search for the feature that you want to prioritize, or create a new feature.
	4. Click on the **Vote for this issue** link. This is located on the right side of the issue under the **People** section. 

<a name="OpenSourceResources"></a>
### 2.2 List of Open Source Resources

*  GitHub
    * <https://github.com/h2oai/h2o-3>
*  JIRA - file issues here ([PUBDEV](https://0xdata.atlassian.net/projects/PUBDEV/issues) contains issues for the current H2O-3 project)
    * <http://jira.h2o.ai>
*  h2ostream community forum - ask your questions here
    * Web: <https://groups.google.com/d/forum/h2ostream>
    * Mail to: [h2ostream@googlegroups.com](mailto:h2ostream@googlegroups.com)
*  Documentation
    * Bleeding edge nightly build page: <https://s3.amazonaws.com/h2o-release/h2o/master/latest.html>
    * FAQ: <http://h2o.ai/product/faq/>
*  Download (pre-built packages)
    * <http://h2o.ai/download>
*  Jenkins
    * <http://test.h2o.ai>
*  Website
    * <http://h2o.ai>
*  Follow us on Twitter, [@h2oai](https://twitter.com/h2oai)


<a name="Artifacts"></a>
## 3. Using H2O-3 Artifacts

Every nightly build publishes R, Python, Java, and Scala artifacts to a build-specific repository.  In particular, you can find Java artifacts in the maven/repo directory.

Here is an example snippet of a gradle build file using h2o-3 as a dependency.  Replace x, y, z, and nnnn with valid numbers.

```
// h2o-3 dependency information
def h2oBranch = 'master'
def h2oBuildNumber = 'nnnn'
def h2oProjectVersion = "x.y.z.${h2oBuildNumber}"

repositories {
  // h2o-3 dependencies
  maven {
    url "https://s3.amazonaws.com/h2o-release/h2o-3/${h2oBranch}/${h2oBuildNumber}/maven/repo/"
  }
}

dependencies {
  compile "ai.h2o:h2o-core:${h2oProjectVersion}"
  compile "ai.h2o:h2o-algos:${h2oProjectVersion}"
  compile "ai.h2o:h2o-web:${h2oProjectVersion}"
  compile "ai.h2o:h2o-app:${h2oProjectVersion}"
}
```

Refer to the latest H2O-3 bleeding edge [nightly build page](http://s3.amazonaws.com/h2o-release/h2o-3/master/latest.html) for information about installing nightly build artifacts.

Refer to the [h2o-droplets GitHub repository](https://github.com/h2oai/h2o-droplets) for a working example of how to use Java artifacts with gradle.

> Note: Stable H2O-3 artifacts are periodically published to Maven Central ([click here to search](http://search.maven.org/#search%7Cga%7C1%7Cai.h2o)) but may substantially lag behind H2O-3 Bleeding Edge nightly builds.

-----

<a name="Building"></a>
## 4. Building H2O-3

Getting started with H2O development requires [JDK 1.7](http://www.oracle.com/technetwork/java/javase/downloads/), [Node.js](https://nodejs.org/), and Gradle.  We use the Gradle wrapper (called `gradlew`) to ensure up-to-date local versions of Gradle and other dependencies are installed in your development directory.

### 4.1. Building from the command line (Quick Start)

To build H2O from the repository, perform the following steps. 


#### Recipe 1: Clone fresh, build, skip tests, and run H2O

```
# Build H2O
git clone https://github.com/h2oai/h2o-3.git
cd h2o-3
./gradlew build -x test

You may encounter problems: e.g. npm missing. Install it:
brew install npm

# Start H2O
java -jar build/h2o.jar

# Point browser to http://localhost:54321

```

#### Recipe 2: Clone fresh, build, and run tests (requires a working install of R)

```
git clone https://github.com/h2oai/h2o-3.git
cd h2o-3
./gradlew syncSmalldata
./gradlew syncRPackages
./gradlew build
```

>**Notes**: 
>
> - Running tests starts five test JVMs that form an H2O cluster and requires at least 8GB of RAM (preferably 16GB of RAM).
> - Running `./gradlew syncRPackages` is supported on Windows, OS X, and Linux, and is strongly recommended but not required. `./gradlew syncRPackages` ensures a complete and consistent environment with pre-approved versions of the packages required for tests and builds. The packages can be installed manually, but we recommend setting an ENV variable and using `./gradlew syncRPackages`. To set the ENV variable, use the following format (where `${WORKSPACE} can be any path):
>  
> ```
> mkdir -p ${WORKSPACE}/Rlibrary
> export R_LIBS_USER=${WORKSPACE}/Rlibrary
> ```

#### Recipe 3:  Pull, clean, build, and run tests

```
git pull
./gradlew syncSmalldata
./gradlew syncRPackages
./gradlew clean
./gradlew build
```

#### Notes

 - We recommend using `./gradlew clean` after each `git pull`.

- Skip tests by adding `-x test` at the end the gradle build command line.  Tests typically run for 7-10 minutes on a Macbook Pro laptop with 4 CPUs (8 hyperthreads) and 16 GB of RAM.

- Syncing smalldata is not required after each pull, but if tests fail due to missing data files, then try `./gradlew syncSmalldata` as the first troubleshooting step.  Syncing smalldata downloads data files from AWS S3 to the smalldata directory in your workspace.  The sync is incremental.  Do not check in these files.  The smalldata directory is in .gitignore.  If you do not run any tests, you do not need the smalldata directory.
- Running `./gradlew syncRPackages` is supported on Windows, OS X, and Linux, and is strongly recommended but not required. `./gradlew syncRPackages` ensures a complete and consistent environment with pre-approved versions of the packages required for tests and builds. The packages can be installed manually, but we recommend setting an ENV variable and using `./gradlew syncRPackages`. To set the ENV variable, use the following format (where `${WORKSPACE} can be any path):

  ```
  mkdir -p ${WORKSPACE}/Rlibrary
  export R_LIBS_USER=${WORKSPACE}/Rlibrary
  ```

#### Recipe 4:  Just building the docs

```
./gradlew clean && ./gradlew build -x test && (export DO_FAST=1; ./gradlew dist)
open target/docs-website/h2o-docs/index.html
```

### 4.2. Setup on all Platforms

> **Note**: The following instructions assume you have installed the latest version of [**Pip**](https://pip.pypa.io/en/latest/installing/#install-or-upgrade-pip), which is installed with the latest version of [**Python**](https://www.python.org/downloads/).  

##### Install required Python packages (prepending with `sudo` if unsuccessful)

    pip install grip
    pip install tabulate
    pip install wheel
    pip install scikit-learn

Python tests require:

    pip install scikit-learn
    pip install numpy
    pip install scipy
    pip install pandas
    pip install statsmodels
    pip install patsy
    pip install future

### 4.3. Setup on Windows

##### Step 1: Download and install [WinPython](https://winpython.github.io). 
  From the command line, validate `python` is using the newly installed package by using `which python` (or `sudo which python`). [Update the Environment variable](https://github.com/winpython/winpython/wiki/Environment) with the WinPython path.
  
###### Step 2: Install required Python packages:

    pip install grip
    pip install tabulate
    pip install wheel

##### Step 3: Install JDK

Install [Java 1.7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html) and add the appropriate directory `C:\Program Files\Java\jdk1.7.0_65\bin` with java.exe to PATH in Environment Variables. To make sure the command prompt is detecting the correct Java version, run:

    javac -version

The CLASSPATH variable also needs to be set to the lib subfolder of the JDK: 

    CLASSPATH=/<path>/<to>/<jdk>/lib

##### Step 4. Install Node.js

Install [Node.js](http://nodejs.org/download/) and add the installed directory `C:\Program Files\nodejs`, which must include node.exe and npm.cmd to PATH if not already prepended. 

##### Step 5. Install R, the required packages, and Rtools:

To install these packages from within an R session, enter:
```R
    install.packages("RCurl")
    install.packages("jsonlite")
    install.packages("statmod")
    install.packages(c("devtools", "roxygen2", "testthat"))
```
Install [R](http://www.r-project.org/) and add the preferred bin\i386 or bin\x64 directory to your PATH.

Note: Acceptable versions of R are >= 2.13 && <= 3.0.0 && >= 3.1.1.

To manually install packages, download the releases of the following R packages: 

- [bitops](http://cran.r-project.org/package=bitops)
- [devtools](http://cran.r-project.org/package=devtools)
- [digest](http://cran.r-project.org/package=digest)
- [Rcpp](http://cran.r-project.org/package=Rcpp)
- [RCurl](http://cran.r-project.org/package=RCurl)
- [jsonlite](http://cran.r-project.org/package=jsonlite)
- [roxygen2](http://cran.r-project.org/package=roxygen2)
- [statmod](http://cran.r-project.org/package=statmod)
- [stringr](http://cran.r-project.org/package=stringr)
- [testthat](http://cran.r-project.org/package=testthat).

```
    cd Downloads
    R CMD INSTALL bitops_x.x-x.zip
    R CMD INSTALL RCurl_x.xx-x.x.zip
    R CMD INSTALL jsonlite_x.x.xx.zip
    R CMD INSTALL statmod_x.x.xx.zip
    R CMD INSTALL Rcpp_x.xx.x.zip
    R CMD INSTALL digest_x.x.x.zip
    R CMD INSTALL testthat_x.x.x.zip
    R CMD INSTALL stringr_x.x.x.zip
    R CMD INSTALL roxygen2_x.x.x.zip
    R CMD INSTALL devtools_x.x.x.zip
```

Finally, install [Rtools](http://cran.r-project.org/bin/windows/Rtools/), which is a collection of command line tools to facilitate R development on Windows.
>**NOTE**: During Rtools installation, do **not** install Cygwin.dll.

##### Step 6. Install [Cygwin](https://cygwin.com/setup-x86_64.exe)
**NOTE**: During installation of Cygwin, deselect the Python packages to avoid a conflict with the Python.org package. 

###### Step 6b. Validate Cygwin
If Cygwin is already installed, remove the Python packages or ensure that Native Python is before Cygwin in the PATH variable. 

##### Step 7. Update or validate the Windows PATH variable to include R, Java JDK, Cygwin. 

##### Step 8. Git Clone [h2o-3](https://github.com/h2oai/h2o-3.git)

If you don't already have a Git client, please install one.  The default one can be found here http://git-scm.com/downloads.  Make sure that command prompt support is enabled before the installation.

Download and update h2o-3 source codes:

    git clone https://github.com/h2oai/h2o-3

##### Step 9. Run the top-level gradle build:

    cd h2o-3
    ./gradlew.bat build

> If you encounter errors run again with `--stacktrace` for more instructions on missing dependencies.


### 4.4. Setup on OS X

If you don't have [Homebrew](http://brew.sh/), we recommend installing it.  It makes package management for OS X easy.

##### Step 1. Install JDK

Install [Java 1.7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html). To make sure the command prompt is detecting the correct Java version, run:

    javac -version

##### Step 2. Install Node.js:

Using Homebrew:

    brew install node

Otherwise, install from the [NodeJS website](http://nodejs.org/download/). 

##### Step 3. Install R and the required packages:

Install [R](http://www.r-project.org/) and add the bin directory to your PATH if not already included.

<a name="InstallRPackagesInUnix"></a>

Install the following R packages: 

- [RCurl](http://cran.r-project.org/package=RCurl)
- [jsonlite](http://cran.r-project.org/package=jsonlite)
- [statmod](http://cran.r-project.org/package=statmod)
- [devtools](http://cran.r-project.org/package=devtools)
- [roxygen2](http://cran.r-project.org/package=roxygen2) 
- [testthat](http://cran.r-project.org/package=testthat).

```   
    cd Downloads
    R CMD INSTALL bitops_x.x-x.tgz
    R CMD INSTALL RCurl_x.xx-x.x.tgz
    R CMD INSTALL jsonlite_x.x.xx.tgz
    R CMD INSTALL statmod_x.x.xx.tgz
    R CMD INSTALL Rcpp_x.xx.x.tgz
    R CMD INSTALL digest_x.x.x.tgz
    R CMD INSTALL testthat_x.x.x.tgz
    R CMD INSTALL stringr_x.x.x.tgz
    R CMD INSTALL roxygen2_x.x.x.tgz
    R CMD INSTALL devtools_x.x.x.tgz
```
To install these packages from within an R session:

    R> install.packages("RCurl")
    R> install.packages("jsonlite")
    R> install.packages("statmod")
    R> install.packages(c("devtools", "roxygen2", "testthat"))

##### Step 4. Git Clone [h2o-3](https://github.com/h2oai/h2o-3.git)

OS X should already have Git installed. To download and update h2o-3 source codes:

    git clone https://github.com/h2oai/h2o-3

##### Step 5. Run the top-level gradle build:

    cd h2o-3
    ./gradlew build

> If you encounter errors run again with `--stacktrace` for more instructions on missing dependencies.

### 4.5. Setup on Ubuntu 14.04

##### Step 1. Install Node.js

    curl -sL https://deb.nodesource.com/setup_0.12 | sudo bash -
    sudo apt-get install -y nodejs

##### Step 2. Install JDK:

Install [Java 1.7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html). Installation instructions can be found here [JDK installation](http://askubuntu.com/questions/56104/how-can-i-install-sun-oracles-proprietary-java-jdk-6-7-8-or-jre). To make sure the command prompt is detecting the correct Java version, run:

    javac -version

#### Step 3. Install R and the required packages:

Installation instructions can be found here [R installation](http://cran.r-project.org).  Click “Download R for Linux”.  Click “ubuntu”.  Follow the given instructions.

To install the required packages, follow the [same instructions as for OS X above](#InstallRPackagesInUnix).

>**Note**: If the process fails to install RStudio Server on Linux, run one of the following: 
>
>`sudo apt-get install libcurl4-openssl-dev`
>
>or
> 
>`sudo apt-get install libcurl4-gnutls-dev`

##### Step 4. Git Clone [h2o-3](https://github.com/h2oai/h2o-3.git)

If you don't already have a Git client:

    sudo apt-get install git

Download and update h2o-3 source codes:

    git clone https://github.com/h2oai/h2o-3

##### Step 5. Run the top-level gradle build:

    cd h2o-3
    ./gradlew build

> If you encounter errors, run again using `--stacktrace` for more instructions on missing dependencies.

> Make sure that you are not running as root, since `bower` will reject such a run.

### 4.6. Setup on Ubuntu 13.10

##### Step 1. Install Node.js

    curl -sL https://deb.nodesource.com/setup_0.12 | sudo bash -
    sudo apt-get install -y nodejs
   

##### Steps 2-4. Follow steps 2-4 for Ubuntu 14.04

### 4.7. Setting up your preferred IDE environment

For users of Intellij's IDEA, generate project files with:

    ./gradlew idea

For users of Eclipse, generate project files with:

    ./gradlew eclipse



###4.7 Setup on CentOS 7

```
cd /opt
sudo wget --no-cookies --no-check-certificate --header "Cookie: gpw_e24=http%3A%2F%2Fwww.oracle.com%2F; oraclelicense=accept-securebackup-cookie" "http://download.oracle.com/otn-pub/java/jdk/7u79-b15/jdk-7u79-linux-x64.tar.gz"

sudo tar xzf jdk-7u79-linux-x64.tar.gz
cd jdk1.7.0_79

sudo alternatives --install /usr/bin/java java /opt/jdk1.7.0_79/bin/java 2

sudo alternatives --install /usr/bin/jar jar /opt/jdk1.7.0_79/bin/jar 2
sudo alternatives --install /usr/bin/javac javac /opt/jdk1.7.0_79/bin/javac 2
sudo alternatives --set jar /opt/jdk1.7.0_79/bin/jar
sudo alternatives --set javac /opt/jdk1.7.0_79/bin/javac
                                                                                                                                                                       
cd /opt

sudo wget http://dl.fedoraproject.org/pub/epel/7/x86_64/e/epel-release-7-5.noarch.rpm
sudo rpm -ivh epel-release-7-5.noarch.rpm

sudo echo "multilib_policy=best" >> /etc/yum.conf
sudo yum -y update

sudo yum -y install R R-devel git python-pip openssl-devel libxml2-devel libcurl-devel gcc gcc-c++ make openssl-devel kernel-devel texlive texinfo texlive-latex-fonts libX11-devel mesa-libGL-devel mesa-libGL nodejs npm python-devel numpy scipy python-pandas

sudo pip install scikit-learn grip tabulate statsmodels wheel

mkdir ~/Rlibrary
export JAVA_HOME=/opt/jdk1.7.0_79
export JRE_HOME=/opt/jdk1.7.0_79/jre
export PATH=$PATH:/opt/jdk1.7.0_79/bin:/opt/jdk1.7.0_79/jre/bin
export R_LIBS_USER=~/Rlibrary

# install local R packages
R -e 'install.packages(c("RCurl","jsonlite","statmod","devtools","roxygen2","testthat"), dependencies=TRUE, repos="http://cran.rstudio.com/")'

cd
git clone https://github.com/h2oai/h2o-3.git
cd h2o-3

# Build H2O
./gradlew syncSmalldata
./gradlew syncRPackages
./gradlew build -x test

```


<a name="Launching"></a>
## 5. Launching H2O after Building

    java -jar build/h2o.jar


<a name="BuildingHadoop"></a>
## 6. Building H2O on Hadoop

Pre-built H2O-on-Hadoop zip files are available on the [download page](http://h2o.ai/download).  Each Hadoop distribution version has a separate zip file in h2o-3.

To build H2O with Hadoop support yourself, first install sphinx for python: `pip install sphinx`
Then start the build by entering  the following from the top-level h2o-3 directory:

    (export BUILD_HADOOP=1; ./gradlew build -x test)
    ./gradlew dist

This will create a directory called 'target' and generate zip files there.  Note that `BUILD_HADOOP` is the default behavior when the username is `jenkins` (refer to `settings.gradle`); otherwise you have to request it, as shown above.


### Adding support for a new version of Hadoop

In the `h2o-hadoop` directory, each Hadoop version has a build directory for the driver and an assembly directory for the fatjar.

You need to:

1.  Add a new driver directory and assembly directory (each with a `build.gradle` file) in `h2o-hadoop`
2.  Add these new projects to `h2o-3/settings.gradle`
3.  Add the new Hadoop version to `HADOOP_VERSIONS` in `make-dist.sh`
4.  Add the new Hadoop version to the list in `h2o-dist/buildinfo.json`

### Debugging HDFS

These are the required steps to debug HDFS in IDEA as a standalone H2O process.

Debugging H2O on Hadoop as a `hadoop jar` hadoop mapreduce job is a difficult thing to do. However, what you can do relatively easily is tweak the gradle settings for the project so that H2OApp has HDFS as a dependency.  Here are the steps:

1.  Make the following changes to gradle build files below
    *  Change the `hadoop-client` version in `h2o-persist-hdfs` to the desired version     
    *  Add `h2o-persist-hdfs` as a dependency to `h2o-app`
1.  Close IDEA
1.  `./gradlew cleanIdea`
1.  `./gradlew idea`
1.  Re-open IDEA
1.  Run or debug H2OApp, and you will now be able to read from HDFS inside the IDE debugger

`h2o-persist-hdfs` is normally only a dependency of the assembly modules, since those are not used by any downstream modules.  We want the final module to define its own version of HDFS if any is desired.

Note this example is for MapR 4, which requires the additional `org.json` dependency to work properly.

```
$ git diff
diff --git a/h2o-app/build.gradle b/h2o-app/build.gradle
index af3b929..097af85 100644
--- a/h2o-app/build.gradle
+++ b/h2o-app/build.gradle
@@ -8,5 +8,6 @@ dependencies {
   compile project(":h2o-algos")
   compile project(":h2o-core")
   compile project(":h2o-genmodel")
+  compile project(":h2o-persist-hdfs")
 }
 
diff --git a/h2o-persist-hdfs/build.gradle b/h2o-persist-hdfs/build.gradle
index 41b96b2..6368ea9 100644
--- a/h2o-persist-hdfs/build.gradle
+++ b/h2o-persist-hdfs/build.gradle
@@ -2,5 +2,6 @@ description = "H2O Persist HDFS"
 
 dependencies {
   compile project(":h2o-core")
-  compile("org.apache.hadoop:hadoop-client:2.0.0-cdh4.3.0")
+  compile("org.apache.hadoop:hadoop-client:2.4.1-mapr-1408")
+  compile("org.json:org.json:chargebee-1.0")
 }
```

-----

<a name="Sparkling"></a>
## 7. Sparkling Water

Sparkling Water combines two open-source technologies: Apache Spark and H2O, our machine learning engine.  It makes H2O’s library of Advanced Algorithms, including Deep Learning, GLM, GBM, K-Means, and Distributed Random Forest, accessible from Spark workflows. Spark users can select the best features from either platform to meet their Machine Learning needs.  Users can combine Spark's RDD API and Spark MLLib with H2O’s machine learning algorithms, or use H2O independently of Spark for the model building process and post-process the results in Spark. 

**Sparkling Water Resources**:

* [Download page for pre-built packages](http://h2o.ai/download/) (Scroll down for Sparkling Water)
* [Sparkling Water GitHub repository](https://github.com/h2oai/sparkling-water)
* [README](https://github.com/h2oai/sparkling-water/blob/master/README.md)
* [Developer documentation](https://github.com/h2oai/sparkling-water/blob/master/DEVEL.md)

<a name="Documentation"></a>
## 8. Documentation

### Live docs website

Visit <http://docs.h2o.ai> for the top-level introduction to documentation on H2O projects.

(Source code for the above page is [here](h2o-docs/src/front/index.html).)

### Generate REST API documentation 

To generate the REST API documentation, use the following commands: 

    cd ~/h2o-3
    cd py
    python ./generate_rest_api_docs.py  # to generate Markdown only
    python ./generate_rest_api_docs.py --generate_html  --github_user GITHUB_USER --github_password GITHUB_PASSWORD # to generate Markdown and HTML

The default location for the generated documentation is `build/docs/REST`. 

If the build fails, try `gradlew clean`, then `git clean -f`. 

### Bleeding edge build documentation

Documentation for each bleeding edge nightly build is available on the [nightly build page](http://s3.amazonaws.com/h2o-release/h2o/master/latest.html).

-----

<a name="Citing"></a>
## 9. Citing H2O

If you use H2O as part of your workflow, please cite your H2O resource(s) using the following BibTex entry:

### H2O Software

	@Manual{h2o_package_or_module,
	    title = {package_or_module_title},
	    author = {The H2O.ai team},
	    year = {year},
	    month = {month},
	    note = {version_information},
	    url = {resource_url},
	}

**Formatted H2O Software citation examples**:

The H2O.ai team (Oct. 2016). _Python Interface for H2O_, Python package version 3.10.0.8. [https://github.com/h2oai/h2o-3].

### H2O Booklets

	@Manual{h2o_booklet_name,
	    title = {booklet_title},
	    author = {list_of_authors},
	    year = {year},
	    month = {month},
	    url = {link_url},
	}

**Formatted booklet citation examples**:

Arora, A., Candel, A., Lanford, J., LeDell, E., and Parmar, V. (Oct. 2016). _Deep Learning with H2O_. [http://h2o.ai/resources]. 

Click, C., Lanford, J., Malohlava, M., Parmar, V., and Roark, H. (Oct. 2016). _Gradient Boosted Models with H2O_. [http://h2o.ai/resources]. 

-----

<a name="Community"></a>
## 10. Community

We will breathe & sustain a vibrant community with the focus of taking a software engineering approach to data science and empowering everyone interested in data to be able to hack data using math and algorithms.
Join us on google groups at [h2ostream](https://groups.google.com/forum/#!forum/h2ostream) and feel free to file issues directly on our [JIRA](http://jira.h2o.ai). 

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
Brandon Hill
Jeff Gambera
Ariel Rao
Viraj Parmar
Kendall Harris
Anand Avati
Jessica Lanford
Alex Tellez
Allison Washburn
Amy Wang
Erik Eckstrand
Neeraja Madabhushi
Sebastian Vidrio
Ben Sabrin
Matt Dowle
Mark Landry
Erin LeDell
Oleg Rogynskyy
Nick Martin
Nancy Jordan 
Nishant Kalonia
Nadine Hussami
Jeff Cramer
Stacie Spreitzer
Vinod Iyengar
Charlene Windom
Parag Sanghavi
```

<a name="Advisors"></a>
## Advisors

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
```

<a name="Investors"></a>
## Investors

```
Jishnu Bhattacharjee, Nexus Venture Partners
Anand Babu Periasamy
Anand Rajaraman
Ash Bhardwaj
Rakesh Mathur
Michael Marks
Egbert Bierman
Rajesh Ambati
```
