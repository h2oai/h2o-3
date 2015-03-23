#H2O-Dev on EC2

 >Tested on Redhat AMI, Amazon Linux AMI, and Ubuntu AMI


##Important Notes

Java is a pre-requisite for H2O; if you do not already have Java installed, make sure to install it before installing H2O. Java is available free on the web,
and can be installed quickly. Although Java is required to 
run H2O, no programming is necessary.
For users that only want to run H2O without compiling their own code, [Java Runtime Environment](https://www.java.com/en/download/) (version 1.6 or later) is sufficient, but for users planning on compiling their own builds, we strongly recommend using [Java Development Kit 1.7](www.oracle.com/technetwork/java/javase/downloads/) or later. 

After installation, launch H2O using the argument `-Xmx`. Xmx is the
amount of memory given to H2O.  If your data set is large,
allocate more memory to H2O by using `-Xmx4g` instead of the default `-Xmx1g`, which will allocate 4g instead of the default 1g to your instance. For best performance, the amount of memory allocated to H2O should be four times the size of your data, but never more than the total amount of memory on your computer.

##Step-by-Step Walk-Through

1. Download the .zip file containing the latest release of H2O-Dev from the
   [H2O downloads page](http://h2o.ai/download/).

2. From your terminal, change your working directory to the same directory as the location of the .zip file.

3. From your terminal, unzip the .zip file.  For example, `unzip h2o-dev-0.2.1.1.zip`. 

4. At the prompt, enter the following commands:

		cd h2o-dev-0.2.1.1  #change working directory to the downloaded file
		java -Xmx4g -jar h2o.jar #run the basic java command to start h2o

5. After a few moments, output similar to the following appears in your terminal window:

		 03-23 14:57:52.930 172.16.2.39:54321     1932   main      INFO: ----- H2O started  -----
		03-23 14:57:52.997 172.16.2.39:54321     1932   main      INFO: Build git branch: rel-serre
		03-23 14:57:52.998 172.16.2.39:54321     1932   main      INFO: Build git hash: 9eaa5f0c4ca39144b1fd180aedb535b5ba08b2ce
		03-23 14:57:52.998 172.16.2.39:54321     1932   main      INFO: Build git describe: jenkins-rel-serre-1
		03-23 14:57:52.998 172.16.2.39:54321     1932   main      INFO: Build project version: 0.2.1.1
		03-23 14:57:52.998 172.16.2.39:54321     1932   main      INFO: Built by: 'jenkins'
		03-23 14:57:52.998 172.16.2.39:54321     1932   main      INFO: Built on: '2015-03-18 12:55:28'
		03-23 14:57:52.998 172.16.2.39:54321     1932   main      INFO: Java availableProcessors: 8
		03-23 14:57:52.999 172.16.2.39:54321     1932   main      INFO: Java heap totalMemory: 245.5 MB
		03-23 14:57:52.999 172.16.2.39:54321     1932   main      INFO: Java heap maxMemory: 3.56 GB
		03-23 14:57:52.999 172.16.2.39:54321     1932   main      INFO: Java version: Java 1.7.0_67 (from Oracle Corporation)
		03-23 14:57:52.999 172.16.2.39:54321     1932   main      INFO: OS   version: Mac OS X 10.10.2 (x86_64)
		03-23 14:57:52.999 172.16.2.39:54321     1932   main      INFO: Machine physical memory: 16.00 GB
		03-23 14:57:52.999 172.16.2.39:54321     1932   main      INFO: Possible IP Address: en5 (en5), fe80:0:0:0:daeb:97ff:feb3:6d4b%10
		03-23 14:57:52.999 172.16.2.39:54321     1932   main      INFO: Possible IP Address: en5 (en5), 172.16.2.39
		03-23 14:57:53.000 172.16.2.39:54321     1932   main      INFO: Possible IP Address: lo0 (lo0), fe80:0:0:0:0:0:0:1%1
		03-23 14:57:53.000 172.16.2.39:54321     1932   main      INFO: Possible IP Address: lo0 (lo0), 0:0:0:0:0:0:0:1
		03-23 14:57:53.000 172.16.2.39:54321     1932   main      INFO: Possible IP Address: lo0 (lo0), 127.0.0.1
		03-23 14:57:53.000 172.16.2.39:54321     1932   main      INFO: Internal communication uses port: 54322
		03-23 14:57:53.000 172.16.2.39:54321     1932   main      INFO: Listening for HTTP and REST traffic on  http://172.16.2.39:54321/
		03-23 14:57:53.001 172.16.2.39:54321     1932   main      INFO: H2O cloud name: 'H2O-Dev-User' on /172.16.2.39:54321, discovery address /238.222.48.136:61150
		03-23 14:57:53.001 172.16.2.39:54321     1932   main      INFO: If you have trouble connecting, try SSH tunneling from your local machine (e.g., via port 55555):
		03-23 14:57:53.001 172.16.2.39:54321     1932   main      INFO:   1. Open a terminal and run 'ssh -L 55555:localhost:54321 H2O-Dev-User@172.16.2.39'
		03-23 14:57:53.001 172.16.2.39:54321     1932   main      INFO:   2. Point your browser to http://localhost:55555
		03-23 14:57:53.211 172.16.2.39:54321     1932   main      INFO: Log dir: '/tmp/h2o-H2O-Dev-User/h2ologs'
		03-23 14:57:53.211 172.16.2.39:54321     1932   main      INFO: Cur dir: '/Users/H2O-Dev-User/Downloads/h2o-dev-0.2.1.1'
		03-23 14:57:53.234 172.16.2.39:54321     1932   main      INFO: HDFS subsystem successfully initialized
		03-23 14:57:53.234 172.16.2.39:54321     1932   main      INFO: S3 subsystem successfully initialized
		03-23 14:57:53.235 172.16.2.39:54321     1932   main      INFO: Flow dir: '/Users/H2O-Dev-User/h2oflows'
		03-23 14:57:53.248 172.16.2.39:54321     1932   main      INFO: Cloud of size 1 formed [/172.16.2.39:54321]
		03-23 14:57:53.776 172.16.2.39:54321     1932   main      WARN: Found schema field which violates the naming convention; name has mixed lowercase and uppercase characters: ModelParametersSchema.dropNA20Cols
		03-23 14:57:53.935 172.16.2.39:54321     1932   main      INFO: Registered: 142 schemas in: 605mS

5. Point your web browser to `http://localhost:54321/` 

The user interface appears in your browser, and now H2O-Dev is ready to go.

**WARNING**: 
  On Windows systems, Internet Explorer is frequently blocked due to
  security settings.  If you cannot reach http://localhost:54321, try using a different web browser, such as Firefox or Chrome.
  
#Launch H2O-Dev from AWS Console

##  



