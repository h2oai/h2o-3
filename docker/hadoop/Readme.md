# README #
The folders contain Dockerfiles for creating images with various Hadoop distributions of various versions. Each version automatically starts the following services:

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
To build the image, execute the following:

```bash
$ ./build.py --distribution <CDH or HDP> --version <VERSION>  # there are also short versions -d and -v available
```

For example to build Docker image for CDH 5.8 execute:

```bash
$ ./build.py --distribution CDH --version 5.8
```

Execute `$ ./build.py --help` to get a full list of all available options.

### Adding Apache Spark ###
It is possible to add multiple versions of Apache Spark. To do so, use the `--spark-version` flag. For example to build image for CDH 5.8 with Spark 2.0 and 2.1 execute:

```bash
$ ./build.py --spark-version 2.0 --spark-version 2.1 --distribution CDH --version 5.8
```

Activating the particular Spark version and adding custom built Spark is discussed in section **Customization**.

## Customization ##
The following snippet shows the most useful options available when running the container:

```bash
$ docker run -it \
    -v path/to/folder/with/custom/startup_scripts/:/startup/ `# mount folder with custom startup scripts` \
    -v path/to/folder/with/custom/h2o_driver/:/home/h2o/h2o  `# mount folder with custom H2O Driver` \
    -v path/to/folder/with/h2o-3/:/home/h2o/h2o-3            `# mount folder with H2O-3 sources` \
    -e ENTER_BASH=TRUE                                       `# enter bash after running tests` \
    -e ACTIVATE_SPARK=2.2.0                                  `# activates the Spark 2.2.0, must be present in the image` \
    -p 8088:8088                                             `# map port of Hadoop UI` \
    -p 54321:54321                                           `# map port of H2O` \
    h2o-<DISTRIBUTION>:<VERSION>                             `# specify which container to run`
```

The `run.py` script can be used to run the container as well. It is a wrapper script, which eases the process of running the container. For more details about the `run.py` script execute:

```
$ ./run.py --help
```

### ENV Variables ###
The docker container contains environment variables which determine the behavior of the container:

* `ENTER_BASH` - if TRUE (**FALSE** by default), runs the `/bin/bash` *after* initialization of HDFS, YARN and H2O; **use `-it` flag when running the docker**

### Custom Startup Scripts ###
Scripts run during startup are located under `/etc/startup`. They are being **naturally sorted** and **run in this order**. However content of this folder should not be edited, instead the custom startup scripts should be added in following manner:

* create a folder containing the required startup scripts on the host machine
* mount this folder on the container under `/startup`


For example, if the `50_custom_startup_script` should be executed during start, execute following:

```bash
$ docker run -v /path/to/folder/containing/script/:/startup h2o-<DISTRIBUTION>:<VERSION>
```
At first the scripts from `/startup` will be copied to the `/etc/startup` folder, then they'll be made executable and then they'll be run.

## H2O ##
H2O can be added to the container in two ways:

1. using the provided custom startup scripts
2. using custom built H2O mounted under `/home/h2o/h2o`

### Using Custom Startup Scripts ###
The `common/custom_startup` folder contains multiple custom startup scripts. If this folder is mounted under `/startup`, the scripts together achieve following:

* `40_1_download_h2o` - downloads the latest nightly build of H2O
  * if `SUPPRESS_H2O_DOWNLOAD` is set to `TRUE`, then does **nothing**
  * by default the download times out after 10 minutes, this might be altered by setting the `H2O_DOWNLOAD_TIMEOUT` (see `timeout` command for more information)
* `40_2_init_h2o` - launches an instance of H2O on Hadoop
  * if there is *no* `/home/h2o/h2o*/h2odriver.jar` then does **nothing**
* `45_1_download_sparkling_water` - downloads the specified version of Sparkling Water
  * if `DOWNLOAD_SW` *does not specify* a version of Sparkling Water, then does **nothing**
* `50_run_tests` - downloads latest nightly build of H2O-3 and runs tests under /home/h2o/h2o-3/h2o-hadoop/tests/python
	* if *no tests* are present, or the folder *does not exist*, **does nothing**

### Using Custom Build ###
It is possible to use custom H2O build. To do so, mount the required build under the `/home/h2o/h2o` folder and set the `SUPPRESS_H2O_DOWNLOAD=TRUE`. The `40_2_init_h2o` will launch an instance of custom build H2O. If the H2O is placed under different folder, then it is necessary to launch H2O manually

## Apache Spark ##
Apache Spark might be present in the container, or a custom Spark might be provided.

To activate a stable version of Spark present in the container, execute:

```
$ /usr/bin/activate_spark_<SPARK_VERSION>
```

This script creates symlink at `/opt/spark-current` to the folder where given Spark is installed. The `/opt/spark-current` folder is already added to `PATH` and the `SPARK_HOME` points to this folder. If the container contains multiple Sparks, it is possible to switch them as necessary while running the container.

### Using Custom Build ###
If a custom build version of Spark should be used, execute:

```
$ /usr/bin/activate_spark_custom
```

If the custom Spark is present at `/opt/spark-custom` then no additional arguments are required. If the Spark installation from different location should be symlinked, provide the path to this installation as an argument of the script. For example:

```
$ /usr/bin/activate_spark_custom /path/to/custom/spark/
```

## Sparkling Water ##
Similar to H2O, Sparkling Water can be also added either from a release or from a custom build.

### Using Release Version ###
It is possible to specify a version of Sparkling Water which should be downloaded and unzipped. To do so, use the `DOWNLOAD_SW` env variable, which should hold the required version of Sparkling Water, for example `DOWNLOAD_SW=2.0.15`

### Using Custom Build ###
If a custom build version of Sparkling Water should be used, it should mount it and a custom startup scripts should be used to initialize the Sparkling Water instance.
