# H2O

[![Join the chat at https://gitter.im/h2oai/h2o-3](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/h2oai/h2o-3?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

H2O is an in-memory platform for distributed, scalable machine learning. H2O uses familiar interfaces like R, Python, Scala, Java, JSON and the Flow notebook/web interface, and works seamlessly with big data technologies like Hadoop and Spark. H2O provides implementations of many popular algorithms such as [GBM](https://en.wikipedia.org/wiki/Gradient_boosting), [Random Forest](https://en.wikipedia.org/wiki/Random_forest), [Deep Neural Networks](https://en.wikipedia.org/wiki/Deep_neural_networks), [Word2Vec](https://en.wikipedia.org/wiki/Word2vec) and [Stacked Ensembles](https://en.wikipedia.org/wiki/Ensemble_learning).  H2O is extensible so that developers can add data transformations and custom algorithms of their choice and access them through all of those clients.  

Data collection is easy. Decision making is hard. H2O makes it fast and easy to derive insights from your data through faster and better predictive modeling. H2O allows online scoring and modeling in a single platform.

H2O-3 (this repository) is the third incarnation of H2O, and the successor to [H2O-2](https://github.com/h2oai/h2o-2).  

### Table of Contents

* [Downloading H2O-3](#Downloading)
* [Open Source Resources](#Resources)
    * [Issue Tracking and Feature Requests](#IssueTracking)
    * [List of H2O Resources](#OpenSourceResources)
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

While most of this README is written for developers who do their own builds, most H2O users just download and use a pre-built version.  If you are a Python or R user, the easiest way to install H2O is via [PyPI](https://pypi.python.org/pypi/h2o) or [Anaconda](https://anaconda.org/h2oai/h2o) (for Python) or [CRAN](https://CRAN.R-project.org/package=h2o) (for R):  

### Python

```bash
pip install h2o
```

### R

```r
install.packages("h2o")
```

For the latest stable, nightly, Hadoop (or Spark / Sparkling Water) releases, or the stand-alone H2O jar, please visit: [https://h2o.ai/download](https://h2o.ai/download)

More info on downloading & installing H2O is available in the [H2O User Guide](http://docs.h2o.ai/h2o/latest-stable/h2o-docs/downloading.html).

<a name="Resources"></a>
## 2. Open Source Resources

Most people interact with three or four primary open source resources:  **GitHub** (which you've already found), **JIRA** (for bug reports and issue tracking), **Stack Overflow** for H2O code/software-specific questions, and **h2ostream** (a Google Group / email discussion forum) for questions not suitable for Stack Overflow.  There is also a **Gitter** H2O developer chat group, however for archival purposes & to maximize accessibility, we'd prefer that standard H2O Q&A be conducted on Stack Overflow.

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
    1.  Create H2O-3 issues in the [PUBDEV](https://0xdata.atlassian.net/projects/PUBDEV/issues) project.  (Note: Sparkling Water questions should be filed under the [SW](https://0xdata.atlassian.net/projects/SW/issues) project.)
*	You can also vote for feature requests and/or other issues. Voting can help H2O prioritize the features that are included in each release.
	1. Go to the [H2O JIRA page](https://0xdata.atlassian.net/).
	2. Click **Log In** to either log in or create an account if you do not already have one.
	3. Search for the feature that you want to prioritize, or create a new feature.
	4. Click on the **Vote for this issue** link. This is located on the right side of the issue under the **People** section.

<a name="OpenSourceResources"></a>
### 2.2 List of H2O Resources

*  GitHub
    * <https://github.com/h2oai/h2o-3>
*  JIRA -- file bug reports / track issues here
    * The [PUBDEV](https://0xdata.atlassian.net/projects/PUBDEV/issues) project contains issues for the current H2O-3 project)
*  Stack Overflow -- ask all code/software questions here
    * <http://stackoverflow.com/questions/tagged/h2o>
*  Cross Validated (Stack Exchange) -- ask algorithm/theory questions here
    * <https://stats.stackexchange.com/questions/tagged/h2o>
*  h2ostream Google Group -- ask non-code related questions here
    * Web: <https://groups.google.com/d/forum/h2ostream>
    * Mail to: [h2ostream@googlegroups.com](mailto:h2ostream@googlegroups.com)
* Gitter H2O Developer Chat
    * <https://gitter.im/h2oai/h2o-3>    
*  Documentation
    * H2O User Guide (main docs): <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/index.html>
    * All H2O documenation links: <http://docs.h2o.ai>
    * Nightly build page (nightly docs linked in page): <https://s3.amazonaws.com/h2o-release/h2o/master/latest.html>
*  Download (pre-built packages)
    * <http://h2o.ai/download>
*  Jenkins (H2O build and test system)
    * <http://test.h2o.ai>
*  Website
    * <http://h2o.ai>
*  Twitter -- follow us for updates and H2O news!
    * <https://twitter.com/h2oai>

*  Awesome H2O -- share your H2O-powered creations with us
   * <https://github.com/h2oai/awesome-h2o>


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


<a name="Building"></a>
## 4. Building H2O-3

Getting started with H2O development requires [JDK 1.7](http://www.oracle.com/technetwork/java/javase/downloads/), [Node.js](https://nodejs.org/), [Gradle](https://gradle.org/), [Python](https://www.python.org/) and [R](http://www.r-project.org/).  We use the Gradle wrapper (called `gradlew`) to ensure up-to-date local versions of Gradle and other dependencies are installed in your development directory.

### 4.1. Before building

Building `h2o` requires a properly set up R environment with [required packages](#InstallRPackagesInUnix) and Python environment with the following packages:

```
grip
colorama
future
tabulate
requests
wheel
```

To install these packages you can use [pip](https://pip.pypa.io/en/stable/installing/) or [conda](https://conda.io/).
If you have troubles installing these packages on *Windows*, please follow section [Setup on Windows](#SetupWin) of this guide.
> (Note: It is recommended to use some virtual environment such as [VirtualEnv](https://virtualenv.pypa.io/), to install all packages. )


### 4.2. Building from the command line (Quick Start)

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
- Running `./gradlew syncRPackages` is supported on Windows, OS X, and Linux, and is strongly recommended but not required. `./gradlew syncRPackages` ensures a complete and consistent environment with pre-approved versions of the packages required for tests and builds. The packages can be installed manually, but we recommend setting an ENV variable and using `./gradlew syncRPackages`. To set the ENV variable, use the following format (where `${WORKSPACE}` can be any path):

  ```
  mkdir -p ${WORKSPACE}/Rlibrary
  export R_LIBS_USER=${WORKSPACE}/Rlibrary
  ```

#### Recipe 4:  Just building the docs

```
./gradlew clean && ./gradlew build -x test && (export DO_FAST=1; ./gradlew dist)
open target/docs-website/h2o-docs/index.html
```

<a name="SetupWin"></a>
### 4.3. Setup on Windows

##### Step 1: Download and install [WinPython](https://winpython.github.io).
  From the command line, validate `python` is using the newly installed package by using `which python` (or `sudo which python`). [Update the Environment variable](https://github.com/winpython/winpython/wiki/Environment) with the WinPython path.

##### Step 2: Install required Python packages:

    pip install grip 'colorama>=0.3.8' future tabulate wheel

##### Step 3: Install JDK

Install [Java 1.7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html) and add the appropriate directory `C:\Program Files\Java\jdk1.7.0_65\bin` with java.exe to PATH in Environment Variables. To make sure the command prompt is detecting the correct Java version, run:

    javac -version

The CLASSPATH variable also needs to be set to the lib subfolder of the JDK:

    CLASSPATH=/<path>/<to>/<jdk>/lib

##### Step 4. Install Node.js

Install [Node.js](http://nodejs.org/download/) and add the installed directory `C:\Program Files\nodejs`, which must include node.exe and npm.cmd to PATH if not already prepended.

##### Step 5. Install R, the required packages, and Rtools:

Install [R](http://www.r-project.org/) and add the bin directory to your PATH if not already included.

<a name="InstallRPackagesInUnix"></a>
Install the following R packages:

- [RCurl](http://cran.r-project.org/package=RCurl)
- [jsonlite](http://cran.r-project.org/package=jsonlite)
- [statmod](http://cran.r-project.org/package=statmod)
- [devtools](http://cran.r-project.org/package=devtools)
- [roxygen2](http://cran.r-project.org/package=roxygen2)
- [testthat](http://cran.r-project.org/package=testthat)

To install these packages from within an R session:

```r
pkgs <- c("RCurl", "jsonlite", "statmod", "devtools", "roxygen2", "testthat")
for (pkg in pkgs) {
  if (! (pkg %in% rownames(installed.packages()))) install.packages(pkg)
}
```
Note that [libcurl](http://curl.haxx.se) is required for installation of the **RCurl** R package.

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
- [testthat](http://cran.r-project.org/package=testthat)

To install these packages from within an R session:

```r
pkgs <- c("RCurl", "jsonlite", "statmod", "devtools", "roxygen2", "testthat")
for (pkg in pkgs) {
  if (! (pkg %in% rownames(installed.packages()))) install.packages(pkg)
}
```
Note that [libcurl](http://curl.haxx.se) is required for installation of the **RCurl** R package.

##### Step 4. Install python and the required packages:

Install python:

    brew install python

Install pip package manager:

    sudo easy_install pip

Next install required pakcages:

    sudo pip install wheel requests 'colorama>=0.3.8' future tabulate  

##### Step 5. Git Clone [h2o-3](https://github.com/h2oai/h2o-3.git)

OS X should already have Git installed. To download and update h2o-3 source codes:

    git clone https://github.com/h2oai/h2o-3

##### Step 6. Run the top-level gradle build:

    cd h2o-3
    ./gradlew build

Note: on a regular machine it may take very long time (about an hour) to run all the tests.

> If you encounter errors run again with `--stacktrace` for more instructions on missing dependencies.

### 4.5. Setup on Ubuntu 14.04

##### Step 1. Install Node.js

    curl -sL https://deb.nodesource.com/setup_0.12 | sudo bash -
    sudo apt-get install -y nodejs

##### Step 2. Install JDK:

Install [Java 1.7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html). Installation instructions can be found here [JDK installation](http://askubuntu.com/questions/56104/how-can-i-install-sun-oracles-proprietary-java-jdk-6-7-8-or-jre). To make sure the command prompt is detecting the correct Java version, run:

    javac -version

##### Step 3. Install R and the required packages:

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


##### Steps 2-4. Follow steps 2-4 for Ubuntu 14.04 (above)

### 4.7. Setup on CentOS 7

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
### 4.8. Setting up your preferred IDE environment

For users of Intellij's IDEA, generate project files with:

    ./gradlew idea

For users of Eclipse, generate project files with:

    ./gradlew eclipse



## 5. Launching H2O after Building

To start the H2O cluster locally, execute the following on the command line:

    java -jar build/h2o.jar

A list of available start-up JVM and H2O options (e.g. `-Xmx`, `-nthreads`, `-ip`), is available in the [H2O User Guide](http://docs.h2o.ai/h2o/latest-stable/h2o-docs/starting-h2o.html#from-the-command-line).

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


### Secure user impersonation

Hadoop supports [secure user impersonation](https://hadoop.apache.org/docs/r2.7.3/hadoop-project-dist/hadoop-common/Superusers.html) through its Java API.  A kerberos-authenticated user can be allowed to proxy any username that meets specified criteria entered in the NameNode's core-site.xml file.  This impersonation only applies to interactions with the Hadoop API or the APIs of Hadoop-related services that support it (this is not the same as switching to that user on the machine of origin).

Setting up secure user impersonation (for h2o):

1.  Create or find an id to use as proxy which has limited-to-no access to HDFS or related services; the proxy user need only be used to impersonate a user
2.  (Required if not using h2odriver) If you are not using the driver (e.g. you wrote your own code against h2o's API using Hadoop), make the necessary code changes to impersonate users (see [org.apache.hadoop.security.UserGroupInformation](http://hadoop.apache.org/docs/r2.8.0/api/org/apache/hadoop/security/UserGroupInformation.html))
3.  In either of Ambari/Cloudera Manager or directly on the NameNode's core-site.xml file, add 2/3 properties for the user we wish to use as a proxy (replace <proxyusername> with the simple user name - not the fully-qualified principal name).
    * `hadoop.proxyuser.<proxyusername>.hosts`: the hosts the proxy user is allowed to perform impersonated actions on behalf of a valid user from
    * `hadoop.proxyuser.<proxyusername>.groups`: the groups an impersonated user must belong to for impersonation to work with that proxy user
    * `hadoop.proxyuser.<proxyusername>.users`: the users a proxy user is allowed to impersonate
    * Example: ```
               <property>
                 <name>hadoop.proxyuser.myproxyuser.hosts</name>
                 <value>host1,host2</value>
               </property>
               <property>
                 <name>hadoop.proxyuser.myproxyuser.groups</name>
                 <value>group1,group2</value>
               </property>
               <property>
                 <name>hadoop.proxyuser.myproxyuser.users</name>
                 <value>user1,user2</value>
               </property>
               ```
4.  Restart core services such as HDFS & YARN for the changes to take effect

Impersonated HDFS actions can be viewed in the hdfs audit log ('auth:PROXY' should appear in the `ugi=` field in entries where this is applicable).  YARN similarly should show 'auth:PROXY' somewhere in the Resource Manager UI.


To use secure impersonation with h2o's Hadoop driver:

*Before this is attempted, see Risks with impersonation, below*

When using the h2odriver (e.g. when running with `hadoop jar ...`), specify `-principal <proxy user kerberos principal>`, `-keytab <proxy user keytab path>`, and `-run_as_user <hadoop username to impersonate>`, in addition to any other arguments needed.  If the configuration was successful, the proxy user will log in and impersonate the `-run_as_user` as long as that user is allowed by either the users or groups configuration property (configured above); this is enforced by HDFS & YARN, not h2o's code.  The driver effectively sets its security context as the impersonated user so all supported Hadoop actions will be performed as that user (e.g. YARN, HDFS APIs support securely impersonated users, but others may not).

#### Precautions to take when leveraging secure impersonation

*  The target use case for secure impersonation is applications or services that pre-authenticate a user and then use (in this case) the h2odriver on behalf of that user.  H2O's Steam is a perfect example: auth user in web app over SSL, impersonate that user when creating the h2o YARN container.
*  The proxy user should have limited permissions in the Hadoop cluster; this means no permissions to access data or make API calls.  In this way, if it's compromised it would only have the power to impersonate a specific subset of the users in the cluster and only from specific machines.
*  Use the `hadoop.proxyuser.<proxyusername>.hosts` property whenever possible or practical.
*  Don't give the proxyusername's password or keytab to any user you don't want to impersonate another user (this is generally *any* user).  The point of impersonation is not to allow users to impersonate each other.  See the first bullet for the typical use case.
*  Limit user logon to the machine the proxying is occurring from whenever practical.
*  Make sure the keytab used to login the proxy user is properly secured and that users can't login as that id (via `su`, for instance)
*  Never set hadoop.proxyuser.<proxyusername>.{users,groups} to '*' or 'hdfs', 'yarn', etc.  Allowing any user to impersonate hdfs, yarn, or any other important user/group should be done with extreme caution and *strongly* analyzed before it's allowed.

#### Risks with secure impersonation

*  The id performing the impersonation can be compromised like any other user id.
*  Setting any `hadoop.proxyuser.<proxyusername>.{hosts,groups,users}` property to '*' can greatly increase exposure to security risk.
*  When users aren't authenticated before being used with the driver (e.g. like Steam does via a secure web app/API), auditability of the process/system is difficult.


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

<a name="Sparkling"></a>
## 7. Sparkling Water

Sparkling Water combines two open-source technologies: Apache Spark and the H2O Machine Learning platform.  It makes H2O’s library of advanced algorithms, including Deep Learning, GLM, GBM, K-Means, and Distributed Random Forest, accessible from Spark workflows. Spark users can select the best features from either platform to meet their Machine Learning needs.  Users can combine Spark's RDD API and Spark MLLib with H2O’s machine learning algorithms, or use H2O independently of Spark for the model building process and post-process the results in Spark.

**Sparkling Water Resources**:

* [Download page for pre-built packages](http://h2o.ai/download/)
* [Sparkling Water GitHub repository](https://github.com/h2oai/sparkling-water)  
* [README](https://github.com/h2oai/sparkling-water/blob/master/README.md)
* [Developer documentation](https://github.com/h2oai/sparkling-water/blob/master/DEVEL.md)

<a name="Documentation"></a>
## 8. Documentation

### Documenation Homepage

The main H2O documentation is the [H2O User Guide](http://docs.h2o.ai/h2o/latest-stable/h2o-docs/index.html).  Visit <http://docs.h2o.ai> for the top-level introduction to documentation on H2O projects.


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


<a name="Citing"></a>
## 9. Citing H2O

If you use H2O as part of your workflow in a publication, please cite your H2O resource(s) using the following BibTex entry:

### H2O Software

	@Manual{h2o_package_or_module,
	    title = {package_or_module_title},
	    author = {H2O.ai},
	    year = {year},
	    month = {month},
	    note = {version_information},
	    url = {resource_url},
	}

**Formatted H2O Software citation examples**:

- H2O.ai (Oct. 2016). _Python Interface for H2O_, Python module version 3.10.0.8. [https://github.com/h2oai/h2o-3](https://github.com/h2oai/h2o-3).
- H2O.ai (Oct. 2016). _R Interface for H2O_, R package version 3.10.0.8. [https://github.com/h2oai/h2o-3](https://github.com/h2oai/h2o-3).
- H2O.ai (Oct. 2016). _H2O_, H2O version 3.10.0.8. [https://github.com/h2oai/h2o-3](https://github.com/h2oai/h2o-3).

### H2O Booklets

H2O algorithm booklets are available at the [Documentation Homepage](http://docs.h2o.ai/h2o/latest-stable/index.html).

	@Manual{h2o_booklet_name,
	    title = {booklet_title},
	    author = {list_of_authors},
	    year = {year},
	    month = {month},
	    url = {link_url},
	}

**Formatted booklet citation examples**:

Arora, A., Candel, A., Lanford, J., LeDell, E., and Parmar, V. (Oct. 2016). _Deep Learning with H2O_. <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/booklets/DeepLearningBooklet.pdf>.

Click, C., Lanford, J., Malohlava, M., Parmar, V., and Roark, H. (Oct. 2016). _Gradient Boosted Models with H2O_. <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/booklets/GBMBooklet.pdf>.


<a name="Community"></a>
## 10. Community

H2O has been built by a great many number of contributors over the years both within H2O.ai (the company) and the greater open source community.  You can begin to contribute to H2O by answering [Stack Overflow](http://stackoverflow.com/questions/tagged/h2o) questions or [filing bug reports](https://0xdata.atlassian.net/projects/PUBDEV/issues).  Please join us!  

### Team & Committers

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
Andrey Spiridonov
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
Navdeep Gill
Lauren DiPerna
Anmol Bal
Mark Chan
Nick Karpov
Avni Wadhwa
Ashrith Barthur
Karen Hayrapetyan
Jo-fai Chow
Dmitry Larko
Branden Murray
Jakub Hava
Wen Phan
Magnus Stensmo
Pasha Stetsenko
Angela Bartz
Mateusz Dymczyk
Micah Stubbs
Ivy Wang
Terone Ward
Leland Wilkinson
Wendy Wong
Nikhil Shekhar
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
