# PROPOSAL:  how to get data for testing

# Data used for testing

Here we spell out the conventions for how to get data needed to test H2O for different test environments.


## Software Assumptions

* You must have the s3cmd tool installed.


## Use cases


### Pre-push tests

Pre-push tests are intended to be fast and run often.
Running time for all tests should be a few minutes.

#### Hardware assumptions:

* 8GB or better RAM.
* Five 1GB JVMs get started.

#### Data assumptions:

* All test data is either generated or exists in the h2o-dev/smalldata directory in the git repo

#### Test environment:

* Driven by java unit test runners

#### How to get the data:

The smalldata directory comes with the repo git clone.  You don't need to do anything.

#### How to run tests:

`./gradlew test`


### Laptop (or small server)

Laptop tests are meant to run stressful workloads on a powerful laptop.
Running time for all tests should be less than an hour.

#### Hardware assumptions:

* At least 12 GB of RAM.  At least 2 CPUs.  (Most H2O developers use a 16 GB Macbook Pro with 4 CPUs and 8 hardware threads.)

#### Data assumptions:

* Max 5 GB of data to download.

* Search for data in the following order:

	1.  path specified by environment variable H2O_BIGDATA + "/laptop"
	1.  h2o-dev/bigdata/laptop (A "magic" directory in your git workspace)
	1.  /home/h2opublictestdata/bigdata/laptop
	1.  /mnt/h2o-public-test-data/bigdata/laptop
	
#### Test environment:

* RUnit tests
* Python tests

#### How to get the data:

`$ ./gradlew syncBigdataLaptop`  

Under the hood, this actually does:

```
mkdir -p bigdata  
cd bigdata  
s3cmd --no-check-md5 sync s3://h2o-public-test-data/bigdata/laptop .  
```

#### How to run tests:

`./gradlew testLaptop`


### Big server

Big server tests are meant to run stressful workloads on modern server hardware with lots of resources.  Many servers may be used at once to reduce running time.

#### Hardware assumptions:

* At least 256 GB of RAM.  Lots of CPUs.

#### Data assumptions:

* Max 50 GB of data to download.
* Take advantage of soft and hard links to make bigger datasets.

* Search for data in the following order:

	1.  path specified by environment variable H2O_BIGDATA + "/bigserver"
	1.  h2o-dev/bigdata/bigserver (A "magic" directory in your git workspace)
	1.  /home/h2opublictestdata/bigdata/bigserver
	1.  /mnt/h2o-public-test-data/bigdata/bigserver

#### Test environment:

* RUnit tests
* Python tests

#### How to get the data:  

CAUTION: Don't do this at home.

`$ ./gradlew syncBigdataBigserver`

Under the hood, this actually does:

```
mkdir -p bigdata  
cd bigdata  
s3cmd --no-check-md5 sync s3://h2o-public-test-data/bigdata/bigserver .
```

#### How to run tests:

`./gradlew testBigserver`


### Cluster in EC2

#### Environment assumptions:

* Infinite RAM.  Lots of cores.

#### Data assumptions:

* Data lives in S3.  Huge. 

* Search for data in the following order:

	1.  s3://h2o-public-test-data/bigdata

#### Test environment:

* JVMs running directly in EC2 instances

#### How to get the data:

You don't.  Just point your test directly to s3://h2o-public-test-data/bigdata.  Definitely do not copy the data to EBS disks.

#### How to run tests:

Jenkins launches them nightly or on-demand.  (Need instructions for how to do this.)
