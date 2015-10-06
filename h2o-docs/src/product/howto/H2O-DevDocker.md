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
  * VirtualBox
  * Latest version of Docker is installed and configured
  * Docker daemon is running - enter all commands below in the Docker daemon window
  * Using `User` directory (not `root`)


##Notes

- Older Linux kernel versions are known to cause kernel panics that break Docker; there are ways around it, but these should be attempted at your own risk. To check the version of your kernel, run `uname -r` at the command prompt. The following walkthrough has been tested on a Mac OS X 10.10.1.
- The Dockerfile always pulls the latest H2O release. 
- The Docker image only needs to be built once. 

**Step 1 - Install and Launch Docker**

Depending on your OS, select the appropriate installation method:

  * [Mac Installation](https://docs.docker.com/installation/mac/#installation)
  * [Ubuntu Installation](https://docs.docker.com/installation/ubuntulinux/)
  * [Other OS Installations](https://docs.docker.com/installation/)


**Step 2 - Create or Download Dockerfile**

  >**Note**: If the following commands do not work, prepend with `sudo`. 

Create a folder on the Host OS to host your Dockerfile by running:

```
mkdir -p /data/h2o-{{branch_name}}
```

Next, either download or create a Dockerfile, which is a build recipe that builds the container.

Download and use our [Dockerfile template](https://github.com/h2oai/h2o-3/blob/master/Dockerfile) by running:

```
cd /data/h2o-{{branch_name}}
wget https://raw.githubusercontent.com/h2oai/h2o-3/master/Dockerfile
```

The Dockerfile:

  * obtains and updates the base image (Ubuntu 14.04) 
  * installs Java 7
  * obtains and downloads the H2O build from H2O's S3 repository
  * exposes ports 54321 and 54322 in preparation for launching H2O on those ports

**Step 3 - Build Docker image from Dockerfile**

From the /data/h2o-{{branch_name}} directory, run:

```
docker build -t "h2oai/{{branch_name}}:v5" .
```

>**Note**: `v5` represents the current version number. 

Because it assembles all the necessary parts for the image, this process can take a few minutes.

**Step 4 - Run Docker Build**

On a Mac, use the argument *-p 54321:54321* to expressly map the port 54321. This is not necessary on Linux.

```
docker run -ti -p 54321:54321 h2o.ai/{{branch_name}}:v5 /bin/bash
```

>**Note**: `v5` represents the version number. 

**Step 5 - Launch H2O**

Navigate to the `/opt` directory and launch H2O. Change the value of `-Xmx` to the amount of memory you want to allocate to the H2O instance. By default, H2O launches on port 54321.

```
cd /opt
java -Xmx1g -jar h2o.jar
```

**Step 6 - Access H2O from the web browser or R**

  * *On Linux*: After H2O launches, copy and paste the IP address and port of the H2O instance into the address bar of your browser. In the following example, the IP is `172.17.0.5:54321`.

```
03:58:25.963 main      INFO WATER: Cloud of size 1 formed [/172.17.0.5:54321 (00:00:00.000)]
```

  * *On OSX*: Locate the IP address of the Docker's network (`192.168.59.103` in the following examples) that bridges to your Host OS by opening a new Terminal window (not a bash for your container) and running ```boot2docker ip```.

```
$ boot2docker ip
192.168.59.103
```

You can also view the IP address (`192.168.99.100` in the example below) by scrolling to the top of the Docker daemon window: 

```

                        ##         .
                  ## ## ##        ==
               ## ## ## ## ##    ===
           /"""""""""""""""""\___/ ===
      ~~~ {~~ ~~~~ ~~~ ~~~~ ~~~ ~ /  ===- ~~~
           \______ o           __/
             \    \         __/
              \____\_______/


docker is configured to use the default machine with IP 192.168.99.100
For help getting started, check out the docs at https://docs.docker.com


```

After obtaining the IP address, point your [browser](localhost:54321) to the specified ip address and port. In R, you can access the instance by installing the latest version of the H2O R package and running:

``` 
library(h2o)
dockerH2O <- h2o.init(ip = "192.168.59.103", port = 54321)
```
