# h2o-ec2

This directory contains scripts to launch an H2O cluster in EC2.

STEP 1:  Set up Amazon Credentials
-----------------------------------------

- Add key info to `~/.bash_profile`:
```
# EC2 keys
export AWS_ACCESS_KEY_ID=""
export AWS_SECRET_ACCESS_KEY=""
export AWS_SSH_PRIVATE_KEY_FILE="/path/to/private_key.pem"
```
- Source the file:
```
source ~/.bash_profile
```

STEP 2:  Install python and boto, if necessary
-----------------------------------------

- Boto: http://boto.readthedocs.org/en/latest/
- Python: https://www.python.org/

**Note:** After following steps 1 & 2 (where you set up your environment) you can run the scripts within the repo using the following command: `./run-all.sh` or you can do it manually by following steps 3-5.

STEP 3:  Build a cluster of EC2 instances
-----------------------------------------

- Edit h2o-cluster-launch-instances.py to suit your specific environment.
- Particularly, you can update the key name, instance type, number of worker nodes, and the instance name:
```
keyName = 'SSH key pair name'
numInstancesToLaunch = 4
instanceType = 'm3.2xlarge'
instanceNameRoot = 'navdeep-instance'
```
- After changing the previous run the following:
```
./h2o-cluster-launch-instances.py
```

STEP 4:  Start H2O Cluster
-------------------------------------------------

- This will distribute the `h2o.jar` file to all the worker nodes, along with your AWS credentials and then start the H2O cluster. Note, the `h2o.jar` is reflective of the latest stable build from H2O.
```
./h2o-cluster-download-h2o.sh
./h2o-cluster-distribute-aws-credentials.sh
./h2o-cluster-start-h2o.sh
```

STEP 5:  Point your browser to H2O
----------------------------------

Point your web browser to the following: 
- http://any one of the public DNS node addresses:54321 or http://any one of the public DNS node addresses:54322 for H2O Flow
- http://any one of the public DNS node addresses:8787 for RStudio
- Note: An example of a DNS is as follows: `ec2-54-221-129-217.compute-1.amazonaws.com`. This can be found in your AWS console under `Public DNS` or in your terminal output next to `Starting on node [x]`.

Stopping and restarting H2O
---------------------------

 - ./h2o-cluster-stop-h2o.sh
 - ./h2o-cluster-start-h2o.sh

Control files (generated when starting the cluster and/or H2O)
--------------------------------------------------------------

    nodes-public
        A list of H2O nodes by public DNS name.

    nodes-private
        A list of H2O nodes by private AWS IP address.

    flatfile.txt
        A list of H2O nodes by (private) IP address and port.

    latest (produced by h2o-cluster-download-h2o.sh)
        Latest build number for the requested branch.

    project_version (produced by h2o-cluster-download-h2o.sh)
        Full project version number for the requested build.

    core-site.xml (produced by ./h2o-cluster-distribute-aws-credentials.sh)
    aws_credentials.properties (produced by ./h2o-cluster-distribute-aws-credentials.sh)
        AWS credentials copied to each instance.


Stopping/Terminating the cluster
--------------------------------

Go to your Amazon AWS console and do the operation manually.
