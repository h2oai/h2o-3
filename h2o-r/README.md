# Using H2O from R
[![CRAN_Status_Badge](http://www.r-pkg.org/badges/version/h2o)](http://cran.r-project.org/web/packages/h2o)
[![Downloads](http://cranlogs.r-pkg.org/badges/h2o)](http://cran.rstudio.com/package=h2o)

## Downloading

We don't yet have a downloadable R artifact for the latest version of H2O. You will need to build it yourself or install the package from [CRAN](https://cran.r-project.org/web/packages/h2o/index.html).


## Building it yourself

The R package is built as part of the normal build process.
In the top-level h2o-3 directory, use `$ ./gradlew build`.

To build the R component by itself, first type `$ cd h2o-r` and then type `$ ../gradlew build`.

The output of the build is a CRAN-like layout in the R directory.


## Installing

###  Installation from the command line

0. Navigate to the top-level `h2o-3` directory: `cd ~/h2o-3`. 
0. Install the H2O package for R: `R CMD INSTALL h2o-r/R/src/contrib/h2o_****.tar.gz`

   **Note**: Do not copy and paste the command above. You must replace the asterisks (*) with the current H2O .tar version number. Look in the `h2o-3/h2o-r/R/src/contrib/` directory for the version number. 

###  Installation from within R

0. Detach any currently loaded H2O package for R.  
`if ("package:h2o" %in% search()) { detach("package:h2o", unload=TRUE) }`  

	```
	Removing package from ‘/Users/H2O_User/.Rlibrary’
	(as ‘lib’ is unspecified)
	```

0. Remove any previously installed H2O package for R.  
`if ("h2o" %in% rownames(installed.packages())) { remove.packages("h2o") }`


	```
	Removing package from ‘/Users/H2O_User/.Rlibrary’
	(as ‘lib’ is unspecified)
	```

0. Install the dependencies for H2O.
   
   **Note**: This list may change as new capabilities are added to H2O. The commands are reproduced below, but we strongly recommend visiting the H2O download page at [h2o.ai/download](http://h2o.ai/download) for the most up-to-date list of dependencies. 
   
	```
  	if (! ("methods" %in% rownames(installed.packages()))) { install.packages("methods") }
	if (! ("statmod" %in% rownames(installed.packages()))) { install.packages("statmod") }
	if (! ("stats" %in% rownames(installed.packages()))) { install.packages("stats") }
	if (! ("graphics" %in% rownames(installed.packages()))) { install.packages("graphics") }
	if (! ("RCurl" %in% rownames(installed.packages()))) { install.packages("RCurl") }
	if (! ("rjson" %in% rownames(installed.packages()))) { install.packages("rjson") }
	if (! ("tools" %in% rownames(installed.packages()))) { install.packages("tools") }
	if (! ("utils" %in% rownames(installed.packages()))) { install.packages("utils") }
	```

0. Install the H2O R package from your build directory.  
  `install.packages("h2o", type="source", repos=(c("http://h2o-release.s3.amazonaws.com/h2o/master/****/R")))`

   **Note**: Do not copy and paste the command above. You must replace the asterisks (*) with the current H2O build number. Refer to the H2O download page at [h2o.ai/download](http://h2o.ai/download) for latest build number. 

	```
	Installing package into ‘/Users/tomk/.Rlibrary’
	(as ‘lib’ is unspecified)
	source repository is unavailable to check versions
	
	The downloaded binary packages are in
	/var/folders/tt/g5d7cr8d3fg84jmb5jr9dlrc0000gn/T//RtmpU2C3LG/downloaded_packages
	```

## Running

###  Start H2O from the command line

Make sure your current directory is the h2o-3 top directory.
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


###  Connect to H2O from within R

To load the H2O package in R, use `library(h2o)`  

```

----------------------------------------------------------------------

Your next step is to start H2O and get a connection object (named
'localH2O', for example):
    > localH2O = h2o.init()

For H2O package documentation, ask for help:
    > ??h2o

After starting H2O, you can use the Web UI at http://localhost:54321
For more information visit http://docs.h2o.ai

----------------------------------------------------------------------

```


To launch H2O, use `localH2O = h2o.init(nthreads = - 1)`  

**Note**: The `nthreads = -1` parameter launches H2O using all available CPUs and is only applicable if you launch H2O locally using R. If you start H2O locally outside of R or start H2O on Hadoop, the `nthreads = -1` parameter is not applicable. 


```
H2O is not running yet, starting it now...

Note:  In case of errors look at the following log files:
    /var/folders/yl/cq5nhky53hjcl9wrqxt39kz80000gn/T//RtmpKkZY3r/h2o_H2O_User_started_from_r.out
    /var/folders/yl/cq5nhky53hjcl9wrqxt39kz80000gn/T//RtmpKkZY3r/h2o_H2O_User_started_from_r.err

java version "1.8.0_25"
Java(TM) SE Runtime Environment (build 1.8.0_25-b17)
Java HotSpot(TM) 64-Bit Server VM (build 25.25-b02, mixed mode)

.Successfully connected to http://127.0.0.1:54321/ 

R is connected to H2O cluster:
    H2O cluster uptime:         1 seconds 405 milliseconds 
    H2O cluster version:        3.1.0.3031 
    H2O cluster name:           H2O_started_from_R_H2O_User_nqf165 
    H2O cluster total nodes:    1 
    H2O cluster total memory:   3.56 GB 
    H2O cluster total cores:    8 
    H2O cluster allowed cores:  2 
    H2O cluster healthy:        TRUE 

Note:  As started, H2O is limited to the CRAN default of 2 CPUs.
       Shut down and restart H2O as shown below to use all your CPUs.
           > h2o.shutdown(localH2O)
           > localH2O = h2o.init(nthreads = -1)
```

#Documentation/References

- [R Package Documentation](http://h2o-release.s3.amazonaws.com/h2o/latest_stable_Rdoc.html)
- [Porting R Scripts Guide](https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/upgrade/H2ODevPortingRScripts.md)
- [R FAQ](https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/howto/FAQ.md#r)
- [YouTube video - Quick Start with R](https://www.youtube.com/watch?list=PLNtMya54qvOHbBdA1x8FNRSpMBEHmhxr0&v=zzV1kTCnmR0)
