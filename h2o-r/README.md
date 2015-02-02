# Using h2o-dev from R


## Downloading

We don't yet have a downloadable R artifact for h2o-dev.
You have to build it yourself.


## Building it yourself

The R package is built as part of the normal build process.
In the top-level h2o-dev directory use `$ ./gradlew build`.

To build the R component by itself, first type `$ cd h2o-r` and then type `$ ../gradlew build`.

The output of the build is an CRAN-like layout in the R directory.


## Installation

### 1.  Installation from the command line

\# Make sure your current directory is the h2o-dev top directory.
`$ R CMD INSTALL h2o-r/R/src/contrib/h2o_0.1.23.99999.tar.gz`

```
* installing to library ‘/Users/tomk/.Rlibrary’
* installing *source* package ‘h2o’ ...
** R
** demo
** inst
** preparing package for lazy loading
Creating a generic function for 't' from package 'base' in package 'h2o'
Creating a generic function for 'match' from package 'base' in package 'h2o'
Creating a generic function for '%in%' from package 'base' in package 'h2o'
Creating a generic function for 'colnames<-' from package 'base' in package 'h2o'
Creating a generic function for 'nrow' from package 'base' in package 'h2o'
Creating a generic function for 'ncol' from package 'base' in package 'h2o'
Creating a generic function for 'colnames' from package 'base' in package 'h2o'
Creating a generic function for 'head' from package 'utils' in package 'h2o'
Creating a generic function for 'tail' from package 'utils' in package 'h2o'
Creating a generic function for 'is.factor' from package 'base' in package 'h2o'
Creating a generic function for 'summary' from package 'base' in package 'h2o'
Creating a generic function for 'mean' from package 'base' in package 'h2o'
Creating a generic function for 'var' from package 'stats' in package 'h2o'
Creating a generic function for 'sd' from package 'stats' in package 'h2o'
Creating a generic function for 'as.factor' from package 'base' in package 'h2o'
Creating a generic function for 'ifelse' from package 'base' in package 'h2o'
Creating a generic function for 'apply' from package 'base' in package 'h2o'
Creating a generic function for 'sapply' from package 'base' in package 'h2o'
** help
*** installing help indices
** building package indices
** testing if installed package can be loaded
* DONE (h2o)
```

### 2.  Installation from within R

\# Detach any currently loaded H2O package for R.  
`if ("package:h2o" %in% search()) { detach("package:h2o", unload=TRUE) }`  

```
[ Output not interesting... ]
```

\# Remove any previously installed H2O package for R.  
`if ("h2o" %in% rownames(installed.packages())) { remove.packages("h2o") }`


```
[ Output not interesting... ]
```

\# Install the H2O R package from your build directory.  
`install.packages("h2o", repos=c("file:///.../h2o-dev/h2o-R/R", getOption("repos")))`

```
Installing package into ‘/Users/tomk/.Rlibrary’
(as ‘lib’ is unspecified)
source repository is unavailable to check versions

The downloaded binary packages are in
	/var/folders/tt/g5d7cr8d3fg84jmb5jr9dlrc0000gn/T//RtmpU2C3LG/downloaded_packages
```

## Running

### 1.  Start H2O from the command line

\# Make sure your current directory is the h2o-dev top directory.
`$ java -jar h2o-app/build/libs/h2o-app.jar`  

```
10-08 12:33:32.410 172.16.2.32:54321     22468  main      INFO: ----- H2O started  -----
10-08 12:33:32.484 172.16.2.32:54321     22468  main      INFO: Build git branch: (unknown)
10-08 12:33:32.484 172.16.2.32:54321     22468  main      INFO: Build git hash: (unknown)
10-08 12:33:32.485 172.16.2.32:54321     22468  main      INFO: Build git describe: (unknown)
10-08 12:33:32.485 172.16.2.32:54321     22468  main      INFO: Build project version: (unknown)
10-08 12:33:32.485 172.16.2.32:54321     22468  main      INFO: Built by: '(unknown)'
10-08 12:33:32.485 172.16.2.32:54321     22468  main      INFO: Built on: '(unknown)'
10-08 12:33:32.485 172.16.2.32:54321     22468  main      INFO: Java availableProcessors: 8
10-08 12:33:32.485 172.16.2.32:54321     22468  main      INFO: Java heap totalMemory: 245.5 MB
10-08 12:33:32.485 172.16.2.32:54321     22468  main      INFO: Java heap maxMemory: 3.56 GB
10-08 12:33:32.486 172.16.2.32:54321     22468  main      INFO: Java version: Java 1.7.0_51 (from Oracle Corporation)
10-08 12:33:32.486 172.16.2.32:54321     22468  main      INFO: OS   version: Mac OS X 10.9.4 (x86_64)
10-08 12:33:32.486 172.16.2.32:54321     22468  main      INFO: Possible IP Address: en0 (en0), fe80:0:0:0:2acf:e9ff:fe1c:ccf%4
10-08 12:33:32.486 172.16.2.32:54321     22468  main      INFO: Possible IP Address: en0 (en0), 172.16.2.32
10-08 12:33:32.486 172.16.2.32:54321     22468  main      INFO: Possible IP Address: lo0 (lo0), fe80:0:0:0:0:0:0:1%1
10-08 12:33:32.486 172.16.2.32:54321     22468  main      INFO: Possible IP Address: lo0 (lo0), 0:0:0:0:0:0:0:1
10-08 12:33:32.486 172.16.2.32:54321     22468  main      INFO: Possible IP Address: lo0 (lo0), 127.0.0.1
10-08 12:33:32.486 172.16.2.32:54321     22468  main      INFO: Internal communication uses port: 54322
10-08 12:33:32.486 172.16.2.32:54321     22468  main      INFO: Listening for HTTP and REST traffic on  http://172.16.2.32:54321/
10-08 12:33:32.487 172.16.2.32:54321     22468  main      INFO: H2O cloud name: 'tomk' on /172.16.2.32:54321, discovery address /225.54.105.89:57654
10-08 12:33:32.487 172.16.2.32:54321     22468  main      INFO: If you have trouble connecting, try SSH tunneling from your local machine (e.g., via port 55555):
10-08 12:33:32.487 172.16.2.32:54321     22468  main      INFO:   1. Open a terminal and run 'ssh -L 55555:localhost:54321 tomk@172.16.2.32'
10-08 12:33:32.487 172.16.2.32:54321     22468  main      INFO:   2. Point your browser to http://localhost:55555
10-08 12:33:32.583 172.16.2.32:54321     22468  main      INFO: Cloud of size 1 formed [/172.16.2.32:54321]
10-08 12:33:32.583 172.16.2.32:54321     22468  main      INFO: Log dir: '/tmp/h2o-tomk/h2ologs'
```


### 2.  Connect to H2O from within R

`library(h2o)`  

```
Loading required package: statmod

----------------------------------------------------------------------

Your next step is to start H2O and get a connection object (named
'localH2O', for example):
    > localH2O = h2o.init()

For H2O package documentation, ask for help:
    > ??h2o

After starting H2O, you can use the Web UI at http://localhost:54321
For more information visit http://docs.0xdata.com

----------------------------------------------------------------------

```


`localH2O = h2o.init()`  

```
Successfully connected to http://127.0.0.1:54321 
R is connected to H2O cluster:
    H2O cluster uptime:        1 minutes 20 seconds 
    H2O cluster version:       (unknown) 
    H2O cluster name:          tomk 
    H2O cluster total nodes:   1 
    H2O cluster total memory:  3.56 GB 
    H2O cluster total cores:   8 
    H2O cluster healthy:       TRUE 
```