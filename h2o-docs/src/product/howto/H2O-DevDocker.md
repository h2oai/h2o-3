# ... Using Docker

This walkthrough describes: 

  * Installing Docker on Mac or Linux OS
  * Creating and modifying the Dockerfile
  * Building a Docker image from the Dockerfile
  * Running the Docker build
  * Launching H2O
  * Accessing H2O from the web browser or R


## Walkthrough
**Prerequisites**

  * Linux kernel version 3.8+

  or

  * Mac OS X 10.6+

Note:  Older Linux kernel versions are known to cause kernel panics and to break Docker; there are ways around it, but attempt at your own risk.
You can check the version of your kernel by running ```uname -r``` in your terminal. The following walkthrough has been tested on a Mac OS X 10.10.1.

**Step 1 - Install and Launch Docker**

  * [Mac Installation](https://docs.docker.com/installation/mac/#installation)
  * [Ubuntu Installation](https://docs.docker.com/installation/ubuntulinux/)
  * [Other OS Installations](https://docs.docker.com/installation/)


**Step 2 - Create or Download Dockerfile**

Create a folder on the Host OS to host your Dockerfile by running:

```
mkdir -p /data/h2o-shannon
```

Then either download or create a Dockerfile. The Dockerfile is essentially a build recipe that will be used to build the container.

Download and use our [Dockerfile template](../../../2015_01_h2o-docker/Dockerfile.txt) by running:

```
cd /data/h2o-shannon
wget http://h2o.ai/blog/2015_01_h2o-docker/Dockerfile
```

The Dockerfile will:

  * Pull and update the base image (Ubuntu 14.04) 
  * Install Java 7
  * Fetch and download the H2O Shannon build from H2O's S3 repository
  * Expose port 54321 and 54322 in preparation for launching H2O on those ports

**Step 3 - Build Docker image from Dockerfile**

From the /data/h2o-shannon directory, run:

```
docker build -t="h2o.ai/shannon" .
```

This process can take a few minutes as it assembles all the necessary parts to the image.

**Step 4 - Run Docker Build**

On a Mac, you must use the argument *-p 54321:54321* to expressly map the port 54321. This is redundant on Linux.

```
docker run -it -p 54321:54321 h2o.ai/shannon
```

**Step 5 - Launch H2O**

Step into the ```/opt``` directory and launch H2O. Change the value of ```-Xmx``` to the amount of memory you want to allocate to the H2O instance. By default, H2O launches on port 54321.

```
cd /opt
java -Xmx1g -jar h2o.jar
```

**Step 6 - Access H2O from the web browser or R**

  * On Linux, when H2O finishes launching, you can copy and paste the IP address and port of the H2O instance. In the following example, that would be 172.17.0.5:54321.

```
03:58:25.963 main      INFO WATER: Cloud of size 1 formed [/172.17.0.5:54321 (00:00:00.000)]
```

  * If it is running on a Mac, you will need to find the IP address of the Docker's network that bridges to your Host OS. To do this, open a new terminal (not a bash for your container) and run ```boot2docker ip```.

```
$ boot2docker ip
192.168.59.103
```

Once you have the IP address, point your [browser](https://]192.168.59.103:54321) to the specified ip address and port. In R, you can access the instance by installing the latest version of the H2O R package and running:

``` 
library(h2o)
dockerH2O <- h2o.init(ip = "192.168.59.103", port = 54321)
```
