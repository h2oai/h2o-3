# Getting Started 

<object width="425" height="344"><param name="movie" value="http://www.youtube.com/v/ZpTydwTWocQ&hl=en&fs=1"></param><param name="allowFullScreen" value="true"></param><embed src="http://www.youtube.com/v/ZpTydwTWocQ&hl=en&fs=1" type="application/x-shockwave-flash" allowfullscreen="true" width="425" height="344"></embed></object>

## Quick Step-by-Step

0. Download the latest release of H2O as a .zip file from the H2O downloads page.
0. From your terminal change your working directory to the same directory where your .zip file is saved.
0. From your terminal, unzip the .zip file. For example: `unzip h2o-1.7.0.520.zip`
0. At the prompt enter the following commands.
   ```sh
   cd h2o-1.7.0.520  #change working directory to the downloaded file
   java -Xmx1g -jar h2o.jar #run the basic java command to start h2o
   ```
0. Wait a few moments and output similar to the following will appear in your terminal window:
   ```sh
   $ java -Xmx3g -jar h2o.jar
   09:54:05.400 main      INFO WATER: ----- H2O started -----
   09:54:05.402 main      INFO WATER: Build git branch: master
   09:54:05.402 main      INFO WATER: Build git hash: e44ee2cec508140fc6312e3d6874df8069eac669
   09:54:05.402 main      INFO WATER: Build git describe: nn-2-3032-ge44ee2c-dirty
   09:54:05.402 main      INFO WATER: Build project version: 2.1.0.99999
   09:54:05.402 main      INFO WATER: Built by: 'Irene'
   09:54:05.402 main      INFO WATER: Built on: 'Tue Feb  4 09:45:26 PST 2014'
   09:54:05.402 main      INFO WATER: Java availableProcessors: 8
   09:54:05.403 main      INFO WATER: Java heap totalMemory: 0.24 gb
   09:54:05.403 main      INFO WATER: Java heap maxMemory: 2.67 gb
   09:54:05.404 main      INFO WATER: Java version: Java 1.7.0_51 (from Oracle Corporation)
   09:54:05.404 main      INFO WATER: OS   version: Mac OS X 10.9.1 (x86_64)
   09:54:05.404 main      INFO WATER: ICE root: '/tmp/h2o-Irene'
   09:54:05.407 main      INFO WATER: Possible IP Address: en0 (en0), fe80:0:0:0:6203:8ff:fe91:950a%4
   09:54:05.407 main      INFO WATER: Possible IP Address: en0 (en0), 192.168.0.4
   09:54:05.408 main      INFO WATER: Possible IP Address: lo0 (lo0), fe80:0:0:0:0:0:0:1%1
   09:54:05.408 main      INFO WATER: Possible IP Address: lo0 (lo0), 0:0:0:0:0:0:0:1
   09:54:05.408 main      INFO WATER: Possible IP Address: lo0 (lo0), 127.0.0.1
   09:54:05.431 main      INFO WATER: Internal communication uses port: 54322 Listening for HTTP and REST traffic on  http: //192.168.0.4:54321/
   09:54:05.471 main      INFO WATER: H2O cloud name: 'Irene'
   09:54:05.472 main      INFO WATER: (v2.1.0.99999) 'Irene' on
   /192.168.0.4:54321, discovery address /229.58.14.243:58682
   09:54:05.472 main      INFO WATER: If you have trouble connecting,
   try SSH tunneling from your local machine (e.g., via port 55555): 1. Open a terminal and run 'ssh -L 55555:localhost:54321 Irene@192.168.0.4'
   2. Point your browser to http://localhost:55555
   09:54:05.475 main      INFO WATER: Cloud of size 1 formed [/192.168.0.4:54321]
   09:54:05.475 main      INFO WATER: Log dir: '/tmp/h2o-Irene/h2ologs'
   ```
0. Point your web-browser to http://localhost:54321/
0. The user interface will appear in your browser, and now H2O is ready to go.

**Warning** On Windows systems, Internet Explorer is frequently blocked due to security settings. If you cannot reach http://localhost:54321 try a different web browser such as Firefox or Chrome.

## Useful Notes

- First time users may need to download and install Java in order to run H2O (https://www.java.com/en/download/). The program is available free on the web, and can be quickly installed. Even though you will use Java to run H2O, no programming is necessary.
- In the Java command entered the term `-Xmx1g` was used. `Xmx` is the amount of memory given to H2O. If your data set is large, give H2O more memory (for example, `-Xmx4g` gives H2O four gigabytes of memory). For best performance, `Xmx` should be 4x the size of your data, but never more than the total amount of memory on your computer.
- Java 1.8 for developers is not supported at this time. Users running H2O, but not modifying and supplementing code, or recompiling the existing code can use Java 1.8


## Working with H2O

Users have several options for interacting with H2O.

A web browser can be used to communicate directly with the embedded
web server inside any of the H2O nodes.  All H2O
nodes contain an embedded web server, and they are all equivalent peers.

Users can also choose to interface with the H2O embedded web server
via the REST API. The REST API accepts HTTP requests and returns
JSON-formatted responses.

H2O can also be used via the H2O for R package,
available from 0xdata. This package uses H2O's REST API
under the hood. Users can install the R package from the  H2O maintained cran. The H2O zip file, and R+ H2O installation details are available at: http://0xdata.com/downloadtable/.

Data sets are not transmitted directly through the REST API. Instead,
the user sends a command (containing an HDFS path to the data set, for example) either through the browser based GUI or via the REST API to ingest
data from disk.

The data set is assigned a KEY in H2O that the user may refer to in
the future commands to the web server.

## Next Steps



