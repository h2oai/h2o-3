# README #
The folders contain Dockerfiles for various Hadoop distributions and various versions. Each version automatically starts the following services:

* **HDFS**
  * namenode
  * secondarynamenode
  * datanode
* **YARN**
  * resourcemanager
  * nodemanager
  * historyserver/timelineserver

It is possible to override the startup sequence. This will be discussed later.

## Building ##
To build the image of Hadoop distribution for required version, execute the following:

```bash
$ ./build.py --distribution <CDH or HDP> --version <VERSION>  # there are also short versions -d and -v available
```

For example to build Docker image for CDH of version 5.8 execute:

```bash
$ ./build.py --distribution CDH --version 5.8
```

### Adding Spark ###
It is possible to add multiple versions of Spark. To do so, use the `--spark-version` flags. For example to builde Docker image for CDH 5.8 with Spark with version 2.0 and 2.1 execute:

```bash
$ ./build.py --spark-version 2.0 --spark-version 2.1 --distribution CDH --version 5.8
```

Activating the particular Spark version is discussed in section **Customization**.

## Running ##
To run the docker in default configuration use:

```bash
$ docker run h2o-<DISTRIBUTION>:<VERSION>
```

This will:

1. start all the services
2. download the latest nightly build of H2O
3. start the H2O
4. run the tests

There are various options how to modify this default behavior.

## Customization ##
The following snippet shows all of the options available when running the container:

```bash
$ docker run -it \
    -v path/to/folder/with/custom/startup_scripts/:/startup/ `# mount folder with custom startup scripts` \
    -v path/to/tests/python/:/home/h2o/tests/python/         `# mount folder with python tests` \
    -e INIT_H2O=FALSE                                        `# do not download and start nighlty build of H2O` \
    -e RUN_TESTS=TRUE                                        `# run tests` \
    -e ENTER_BASH=TRUE                                       `# enter bash after running tests` \
    -e ACTIVATE_SPARK=2.0                                    `# activates Spark 2.0` \
    -p 8088:8088                                             `# map port of Hadoop UI` \
    -p 54321:54321                                           `# map port of H2O` \
    h2o-<DISTRIBUTION>:<VERSION>                             `# specify which container to run`
```

The `run.py` script can be used to run the container as well. It is a wrapper script, which eases the process of running the container. For more details about the `run.py` script execute:

```
$ ./run.py --help
```

### Using Particular Spark Version ###
The version of Spark can be specified via the environment variable `ACTIVATE_SPARK`. However it might be necessary to change the Spark version while running the contanier (for example because of tests). To do so, execute:

```
$ /opt/activate_spark_<SPARK_VERSION>
```
Use the script for the required version of Spark.

### ENV Variables ###
The docker container contains several environment variables, which determine the behavior of the container:

* `INIT_H2O` - if TRUE (default), downloads the latest nightly build of H2O and runs it
* `RUN_TESTS` - if TRUE (default), runs the tests (discussed later)
* `ENTER_BASH` - if TRUE (**FALSE** by default), runs the bash *after* initialization of HDFS, YARN and H2O and executing tests; **use `-it` flag when running the docker**

### Tests ###
By default the container will run all python scripts located under `/home/h2o/tests/python`. All of these will be run under the **user h2o**. These scripts are being run **after** HDFS, YARN and H2O initialization. The required scripts should be added in following manner:

* create a folder containing the required python scripts on the host machine
* mount this folder on the container on path `/home/h2o/tests/python` using the `-v` flag when running the docker

For example, if `custom_test.py` should be run, execute following:

```bash
$ docker run -v /path/to/tests:/startup h2o-<DISTRIBUTION>:<VERSION>
```

### Custom startup scripts ###
Scripts run during startup are located under `/etc/startup`. They are being **naturally sorted** and **run in this order**. However content of this folder should not be edited, instead, the custom startup scripts should be added in following manner:

* create a folder containing the required startup scripts on the host machine
* mount this folder on the container on path `/startup` using the `-v` flag when running the docker

For example, if we want to run the `50_custom_startup_script` during start of the docker, execute following:

```bash
$ docker run -v /path/to/folder/containing/script/:/startup h2o-<DISTRIBUTION>:<VERSION>
```
At first the script will be copied to the `/etc/startup` folder, then it will be made executable and it will be run.

An example, when this behaviour is desired, is when the H2O driver from the host machine should be used instead of the driver from nightly build. To achieve this, execute following:

1. **download** and prepare the H2O driver on the host machine
2. **create startup** script, which will start the H2O on Hadoop
3. **mount** the folder containing the **H2O driver**
4. **mount** the folder containing the **startup script**
5. set the **ENV variable** `INIT_H2O` to `FALSE`
6. **run** the docker
