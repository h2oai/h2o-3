# Compiling H2O from source

These instructions assume you are using Linux, MacOSX, or Cygwin (on Windows).

Create a git clone of the H2O repository.

    git clone https://github.com/0xdata/h2o.git

Install necessary dependencies.

    pip install -r requirements.txt [--user]

Build H2O from source.  You must have Java JDK 1.6 or higher, Sbt 0.13.1 or higher. If pdflatex is installed on your system, you must have inconsolata latex package and ptmr8t font for latex. R packages 'RCurl', 'rjson' and 'statmod.'

After the build finishes, some JUnit tests will run automatically.

    $ cd h2o
    $ make

(Output below)

    PROJECT_VERSION is 99.99.99.99999
    make build PROJECT_VERSION=99.99.99.99999
    make build_h2o PROJECT_VERSION=99.99.99.99999
    (export PROJECT_VERSION=99.99.99.99999; ./build.sh doc)
    cleaning...
    - wiping tmp...
    building classes...
    warning: [options] bootstrap class path not set in conjunction with -source 1.6
    Note: Some input files use unchecked or unsafe operations.
    Note: Recompile with -Xlint:unchecked for details.
    1 warning
    building initializer...
    ~/Documents/h2o/lib ~/Documents/h2o
    ~/Documents/h2o
    creating jar file... target/h2o.jar
    copying jar file... target/h2o.jar to target/h2o-10.42.01-072913.jar
    creating src jar file... target/h2o-sources.jar
    copying jar file... target/h2o-sources.jar to target/h2o-sources-10.42.01-072913.jar
    creating javadoc files...
    make -C hadoop build PROJECT_VERSION=99.99.99.99999
    rm -fr classes target
    mkdir classes
    mkdir target
    make build_inner HADOOP_VERSION=mapr2.1.3
    mkdir classes/mapr2.1.3
    javac -source 1.6 -target 1.6 -sourcepath src/main/java -classpath
    "../lib/log4j/log4j-1.2.15.jar:../target/h2o.jar:../lib/hadoop/mapr2.1.3/hadoop-0.20.2-dev-core.jar"
    -d classes/mapr2.1.3 src/main/java/water/hadoop/*.java
    warning: [options] bootstrap class path not set in conjunction with -source 1.6
    1 warning
    jar cf target/h2odriver_mapr2.1.3.jar -C classes/mapr2.1.3 .
    make build_inner HADOOP_VERSION=cdh3
    mkdir classes/cdh3
    javac -source 1.6 -target 1.6 -sourcepath src/main/java -classpath
    "../lib/log4j/log4j-1.2.15.jar:../target/h2o.jar:../lib/hadoop/cdh3/hadoop-core-0.20.2-cdh3u6.jar" -d
    classes/cdh3 src/main/java/water/hadoop/*.java
    warning: [options] bootstrap class path not set in conjunction with -source 1.6
    1 warning
    jar cf target/h2odriver_cdh3.jar -C classes/cdh3 .
    make build_inner HADOOP_VERSION=cdh4
    mkdir classes/cdh4
    javac -source 1.6 -target 1.6 -sourcepath src/main/java -classpath
    "../lib/log4j/log4j-1.2.15.jar:../target/h2o.jar:../lib/hadoop/cdh4/hadoop-common.jar:../
    lib/hadoop/cdh4/hadoop-mapreduce-client-core-2.0.0-cdh4.2.0.jar" -d
    classes/cdh4 src/main/java/water/hadoop/*.java
    warning: [options] bootstrap class path not set in conjunction with -source 1.6
    ../lib/hadoop/cdh4/hadoop-common.jar(org/apache/hadoop/fs/Path.class):
    warning: Cannot find annotation method 'value()' in type
    'LimitedPrivate': class file for
    org.apache.hadoop.classification.InterfaceAudience not found
    Note: src/main/java/water/hadoop/h2odriver.java uses or overrides a deprecated API.
    Note: Recompile with -Xlint:deprecation for details.
    2 warnings
    jar cf target/h2odriver_cdh4.jar -C classes/cdh4 .
    make build_inner HADOOP_VERSION=cdh4_yarn
    mkdir classes/cdh4_yarn
    javac -source 1.6 -target 1.6 -sourcepath src/main/java -classpath
    "../lib/log4j/log4j-1.2.15.jar:../target/h2o.jar:../lib/hadoop/cdh4_yarn/common/
    hadoop-common-2.0.0-cdh4.3.0.jar:../lib/hadoop/cdh4_yarn/mapreduce2/hadoop-mapreduce-client-
    core-2.0.0-cdh4.3.0.jar" -d
    classes/cdh4_yarn src/ main/java/water/hadoop/*.java
    warning: [options] bootstrap class path not set in conjunction with -source 1.6
    ../lib/hadoop/cdh4_yarn/common/hadoop-common-2.0.0-cdh4.3.0.jar(org/apache/hadoop/fs/Path.class):
    warning: Cannot find annotation method 'value()' in type
    'LimitedPrivate': class file for org.apache.hadoop.classification.InterfaceAudience not found
    Note: src/main/java/water/hadoop/h2odriver.java uses or overrides a deprecated API.
    Note: Recompile with -Xlint:deprecation for details.
    2 warnings
    jar cf target/h2odriver_cdh4_yarn.jar -C classes/cdh4_yarn .
    rm -rf ../target/hadoop
    mkdir ../target/hadoop
    cp README.txt ../target/hadoop
    cp target/* ../target/hadoop
    make -C R build PROJECT_VERSION=99.99.99.99999
    sed 's/SUBST_PROJECT_VERSION/99.99.99.99999/' DESCRIPTION.template > h2o-package/DESCRIPTION
    R CMD build h2o-package
    * checking for file 'h2o-package/DESCRIPTION' ... OK
    * preparing 'h2o':
    * checking DESCRIPTION meta-information ... OK
    * checking for LF line-endings in source and make files
    * checking for empty or unneeded directories
    * building 'h2o_99.99.99.99999.tar.gz'

    mkdir -p ../target/R
    mv h2o_99.99.99.99999.tar.gz ../target/R
    make -C launcher build PROJECT_VERSION=99.99.99.99999
    rm -fr classes
    rm -fr target
    mkdir classes
    mkdir target
    javac -source 1.6 -target 1.6 -sourcepath src -d classes src/*.java
    warning: [options] bootstrap class path not set in conjunction with -source 1.6
    1 warning
    jar cmf manifest.txt target/H2OLauncher.jar -C classes .
    mkdir -p ../target/launcher
    mv target/H2OLauncher.jar ../target/launcher
    make package
    rm -fr target/h2o-99.99.99.99999
    mkdir target/h2o-99.99.99.99999
    cp -rp target/R target/h2o-99.99.99.99999
    cp -rp target/hadoop target/h2o-99.99.99.99999
    cp -p target/h2o.jar target/h2o-99.99.99.99999
    cp -p target/h2o-sources.jar target/h2o-99.99.99.99999
    (cd target; zip -r h2o-99.99.99.99999.zip h2o-99.99.99.99999)
    adding: h2o-99.99.99.99999/ (stored 0%)
    adding: h2o-99.99.99.99999/h2o-sources.jar (deflated 3%)
    adding: h2o-99.99.99.99999/h2o.jar (deflated 0%)
    adding: h2o-99.99.99.99999/hadoop/ (stored 0%)
    adding: h2o-99.99.99.99999/hadoop/h2odriver_cdh3.jar (deflated 8%)
    adding: h2o-99.99.99.99999/hadoop/h2odriver_cdh4.jar (deflated 8%)
    adding: h2o-99.99.99.99999/hadoop/h2odriver_cdh4_yarn.jar (deflated 8%)
    adding: h2o-99.99.99.99999/hadoop/h2odriver_mapr2.1.3.jar (deflated 8%)
    adding: h2o-99.99.99.99999/hadoop/README.txt (deflated 57%)
    adding: h2o-99.99.99.99999/R/ (stored 0%)
    adding: h2o-99.99.99.99999/R/h2o_99.99.99.99999.tar.gz (deflated 0%)
    rm -fr target/h2o-99.99.99.99999
    make build_installer PROJECT_VERSION=99.99.99.99999
    make -C installer build PROJECT_VERSION=99.99.99.99999
    InstallBuilder not found, skipping creation of windows and mac installer packages.
    rm -fr target/h2o-99.99.99.99999-osx-installer.app
    rm -f target/h2o-*-windows-installer.exe.dmg



The build produces target/h2o.jar.  Now run h2o.jar from the
command line.  Note that Xmx is the amount of memory given to
H2O. If your data set is large, increase the number immediately
following Xmx from the default of 2. As a rule, the amount of
memory given should be about 4 times the size of your data, but no
larger than the total memory of your computer.

    $ java -Xmx2g -jar target/h2o.jar

(Output below)

    04:57:15.900 main      INFO WATER: ----- H2O started -----
    04:57:15.901 main      INFO WATER: Build git branch: master
    04:57:15.901 main      INFO WATER: Build git hash: 9b956b258f276b5187cecde2be193c6485bd4517
    04:57:15.902 main      INFO WATER: Build git describe: 9b956b2
    04:57:15.902 main      INFO WATER: Built by: 'tomk'
    04:57:15.902 main      INFO WATER: Built on: 'Tue Jul 23 14:13:38 PDT 2013'
    04:57:15.902 main      INFO WATER: Java availableProcessors: 8
    04:57:15.906 main      INFO WATER: Java heap totalMemory: 0.08 gb
    04:57:15.906 main      INFO WATER: Java heap maxMemory: 0.99 gb
    04:57:15.918 main      INFO WATER: ICE root: '/tmp'
    04:57:15.955 main      WARN WATER: Multiple local IPs detected:
    +                                    /172.16.175.1  /192.168.183.1  /192.168.1.28
    +                                  Attempting to determine correct address...
    +                                  Using /192.168.1.28
    04:57:15.997 main      INFO WATER: Internal communication uses port: 54322
    +                                  Listening for HTTP and REST traffic on  http://192.168.1.28:54321/
    04:57:16.029 main
    04:57:16.029 main      INFO WATER: 192.168.1.28:54321, discovery address /236.151.114.91:60567
    04:57:16.031 main      INFO WATER: Cloud of size 1 formed [/192.168.1.28:54321]
    04:57:16.032 main      INFO WATER: Log dir: '/tmp/h2ologs'

Point your web browser to the HTTP URL http://your-ip-address:54321; H2O will run from there.

## Updating H2O from Github

Change directory to H2O directory created when the git repository was originally cloned.

Pull the latest code, and update

    $ git pull
    $ make

