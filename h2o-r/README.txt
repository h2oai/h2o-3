H2O in R
------------


These instructions assume you are using R 2.13.0 or later.  

**STEP 1**

The download package can be obtained by clicking on the button "Download H2O" at http://0xdata.com/h2o <http://0xdata.com/h2o.

Unzip the downloaded h2o zip file


**STEP 2: Console Users and Studio Users should follow the same steps: **

In the R console install the package by 

1. Visiting http://0xdata.com/downloadtable/
2. Choosing the version of H2O appropriate for their environment 
3. Copy and pasting the R command shown below the downloadable zip file on the download page for the version of their choice into their R console. 


Correctly following the above steps will return output similar to the following: 

trying URL 'http://s3.amazonaws.com/h2o-release/h2o/master/1247/R/bin/macosx/contrib/3.0/h2o_2.3.0.1247.tgz'
Content type 'application/x-tar; charset=binary' length 36702378 bytes (35.0 Mb)
opened URL
==================================================
downloaded 35.0 Mb


**STEP 3**

Start an instance of H2O. If you have questions about how to do this see the notes provided at the bottom of the page for starting from a zip file.

If users choose to not start an instance of H2O prior to attempting to connect to H2O through R, an instance will be started automatically for them at ip: localhost, port: 54321.

*Users should be aware that in order for H2O to successfully run through R, an instance of H2O must also simultaneously be running. If the instance of H2O is stopped, the R program will no longer run, and work done will be lost.* 


**STEP 4** 

call the H2O package in the R environment, start the connection between R and H2O at ip: localhost and port: 54321


  >library(h2o)

  >localH2O = h2o.init()



**STEP 6** 

Here is an example of using the above object in an H2O call in R



  >irisPath = system.file("extdata", "iris.csv", package="h2o")
  >iris.hex = h2o.importFile(localH2O, path = irisPath, key = "iris.hex")
  >summary(iris.hex)





Getting started from a zip file
-------------------------------


1. Download the latest release of H2O as a .zip file from the H2O website http://0xdata.com/h2O/.

2. From your terminal change your working directory to the same directory where your .zip file is saved.

3. From your terminal, unzip the .zip file.  For example:


  unzip h2o-1.7.0.520.zip

4. At the prompt enter the following commands.  (Choose a unique name (use the -name option) for yourself if other people might be running H2O in your network.)


  cd h2o-1.7.0.520 
  java -Xmx1g -jar h2o.jar -name mystats-cloud

5. Wait a few moments and the output similar to the following will appear in your terminal window:



  03:05:45.311 main      INFO WATER: ----- H2O started -----
  03:05:45.312 main      INFO WATER: Build git branch: master
  03:05:45.312 main      INFO WATER: Build git hash: f253798433c109b19acd14cb973b45f255c59f3f
  03:05:45.312 main      INFO WATER: Build git describe: f253798
  03:05:45.312 main      INFO WATER: Build project version: 1.7.0.520
  03:05:45.313 main      INFO WATER: Built by: 'jenkins'
  03:05:45.313 main      INFO WATER: Built on: 'Thu Sep 12 00:01:52 PDT 2013'
  03:05:45.313 main      INFO WATER: Java availableProcessors: 8
  03:05:45.321 main      INFO WATER: Java heap totalMemory: 0.08 gb
  03:05:45.321 main      INFO WATER: Java heap maxMemory: 0.99 gb
  03:05:45.322 main      INFO WATER: ICE root: '/tmp/h2o-tomk'
  03:05:45.364 main      INFO WATER: Internal communication uses port: 54322
  +                                  Listening for HTTP and REST traffic on  http://192.168.1.52:54321/
  03:05:45.409 main      INFO WATER: H2O cloud name: 'mystats-cloud'
  03:05:45.409 main      INFO WATER: (v1.7.0.520) 'mystats-cloud' on /192.168.1.52:54321, discovery address /236.151.114.91:60567
  03:05:45.411 main      INFO WATER: Cloud of size 1 formed [/192.168.1.52:54321]
  03:05:45.543 main      INFO WATER: Log dir: '/tmp/h2o-tomk/h2ologs'



Useful Notes
""""""""""""   

First time users may need to download and install Java
in order to run H2O. H2O currently supports any Java beyond Java 6. 
The program is available free on the web, 
and can be quickly installed. Even though you will use Java to 
run H2O, no programming is necessary. 

In the Java command entered to run H2O:

java -Xmx1g -jar h2o.jar

the term -Xmx1g was used. Xmx is the
amount of memory given to H2O.  If your data set is large,
give H2O more memory (for example, -Xmx4g gives H2O four gigabytes of
memory).  For best performance, Xmx should be 4x the size of your
data, but never more than the total amount of memory on your
computer. For larger data sets, running on a server or service 
with more memory available for computing is recommended. 













