
This directory contains scripts to launch an H2O cluster in EC2.
You must install the boto python library.

http://boto.readthedocs.org/en/latest/
http://www.amazon.com/Python-and-AWS-Cookbook-ebook/dp/B005ZTO0UW/ref=sr_1_1?ie=UTF8&qid=1379879111&sr=8-1&keywords=python+aws


[ STEP 0:  Install python and boto, if necessary. ]


STEP 1:  Build a cluster of EC2 instances
-----------------------------------------

Note:  Run this from a host that can access the nodes via public DNS name.

Edit h2o-cluster-launch-instances.py to suit your specific environment.
At a minimum, you need to specify an ssh key name and a security group name.

% ./h2o-cluster-launch-instances.py
% ./h2o-cluster-distribute-h2o.sh  --OR--  ./h2o-cluster-download-h2o.sh
% [optional] ./h2o-cluster-distribute-aws-credentials.sh

Note:  If you run h2o with an IAM role, it is not necessary to distribute
       the aws-credentials to all the nodes in the cluster. The temporary
       access key should be accessible by later version of H2O.

Note:  Download may be faster than distribute, since download pulls from S3.

Note:  Distributing the AWS credentials copies the Amazon AWS_ACCESS_KEY_ID
       and AWS_SECRET_ACCESS_KEY to the instances.  This enables S3 and S3N
       access.  Take precaution when putting your security keys in the 
       cloud.


STEP 2:  Start H2O, one H2O node per EC2 instance
-------------------------------------------------

% ./h2o-cluster-start-h2o.sh
(wait 60 seconds)


STEP 3:  Point your browser to H2O
----------------------------------

Point your web browser to 
    http://<any one of the public DNS node addresses>:54321


Stopping and restarting H2O
---------------------------
% ./h2o-cluster-stop-h2o.sh
% ./h2o-cluster-start-h2o.sh


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
