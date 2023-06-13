This is an experimental support for custom persist implementation relying on python and GraalVM

Currently, our example uses S3 and boto3 python library to download file locally and parse into H2O.

Installation Steps:

1) Download GraalVM
wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.2.0/graalvm-ce-java17-linux-amd64-22.2.0.tar.gz
tar xfz graalvm-ce-java17-linux-amd64-22.2.0.tar.gz
export GRAAL_HOME=$(ls -d graalvm-*)

2) Create Python environment with boto3 
$GRAAL_HOME/bin/gu install python
$GRAAL_HOME/bin/graalpython -m venv venv
(source venv/bin/activate && pip install boto3)

3) Set AWS credentials into environment variables
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...

4) Run H2O with GraalVM
$GRAAL_HOME/bin/java -jar ../../build/h2o.jar

5) Import data from "drive" (in our case backed by S3)
python
> import h2o
> h2o.connect()
> h2o.import_file("drive://h2o-public-test-data/smalldata/iris/iris.csv")
