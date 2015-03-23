#H2O-Dev on EC2

 >Tested on Redhat AMI, Amazon Linuz AMI, and Ubuntu AMI

##Launch H2O-Dev

###Selecting the Operating System and Virtualization Type

Select your operating system and the virtualization type of the prebuilt AMI on Amazon. If you are using Windows, you will need to use a hardware-assisted virtual machine (HVM). If you are using Linux, you can choose between para-virtualization (PV) and HVM. These selections determine the type of instances you can launch. For more information about virtualization types, refer to [Amazon](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/virtualization_types.html). 

###Configuring the Instance

0. Select the IAM role and policy to use to launch the instance. H2O detects the temporary access keys associated with the instance, so you don't need to copy your AWS credentials to the instances. 

0. When launching the instance, select an accessible key pair. 


####(Windows Users) Tunneling into the Instance

For Windows users that do not have the ability to use `ssh` from the terminal, either download Cygwin or a Git Bash that has the capability to run `ssh`:

`ssh -i amy_account.pem ec2-user@54.165.25.98`

Otherwise, download PuTTY and follow these instructions:

0. Launch the PuTTY Key Generator. 
0. Load your downloaded AWS pem key file. 
   **Note:** To see the file, change the browser file type to "All". 
0. Save the private key as a .ppk file. 
0. Launch the PuTTY client. 
0. In the *Session* section, enter the host name or IP address. For Ubuntu users, the default host name is `ubuntu@<ip-address>`. For Linux users, the default host name is `ec2-user@<ip-address>`.  
0. Select *SSH*, then *Auth* in the sidebar, and click the **Browse** button to select the private key file for authentication. 
0. Start a new session and click the **Yes** button to confirm caching of the server's rsa2 key fingerprint and continue connecting. 


###Downloading Java and H2O


0. Download [Java](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html
) (JDK 1.7 or later) if it is not already available on the instance. 
0. To download H2O, run the `wget` command with the link to the zip file available on our [website](http://h2o.ai/download/) by copying the link associated with the **Download** button for the selected H2O-Dev build. 
	
		wget http://h2o-release.s3.amazonaws.com/h2o-dev/rel-serre/1/index.html
		unzip h2o-dev-0.2.1.1.zip
		cd h2o-dev-0.2.1.1
		java -Xmx4g -jar h2o.jar
0. From your browser, navigate to `<Private_IP_Address>:54321` or `<Public_DNS>:54321` to use H2O's web interface. 



##Launch H2O-Dev from the Command Line

**Prerequisites**

 - Before running H2O-Dev on an EC2 cluster, install the boto python library. The boto library is required for running scripts. For more information, refer to: 

   - [Python Boto Documentation](http://boto.readthedocs.org/en/latest/)
   - [Amazon AWS Text](http://www.amazon.com/Python-and-AWS-Cookbook-ebook/dp/B005ZTO0UW/ref=sr_1_1?ie=UTF8&qid=1379879111&sr=8-1&keywords=python+aws)
 
- Verify that the TCP port (54321) and the UDP port (54322) are available for use by H2O. 

- Install a JRE or preferred Oracle [JDK](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html
)

- To use S3 buckets, create an appropriate core-site.xml file and provide the flag `-hdfs_config core-site.xml`. See step 5 for more information. 

0. Edit the `h2o-cluster-launch-instances.py` launch script for parameter changes. For more information, refer to the [EC2 Glossary](http://docs.h2o.ai/deployment/ec2_glossary.html#ec2-glossary). 

		# Environment variables you MUST set (either here or by passing them in).
		# -----------------------------------------------------------------------
		#
		os.environ['AWS_ACCESS_KEY_ID'] = '...'
		os.environ['AWS_SECRET_ACCESS_KEY'] = '...'
		os.environ['AWS_SSH_PRIVATE_KEY_FILE'] = '/path/to/private_key.pem'

		# Launch EC2 instances with an IAM role
		# --------------------------------------
		# Change either but not both the IAM Profile Name.
		iam_profile_resource_name = None
		iam_profile_name = 'testing_role'
		...
		# SSH key pair name.
		keyName = 'testing_key'
		securityGroupName = 'SecurityDisabled'
		...
		numInstancesToLaunch = 2
		instanceType = 't1.micro'
		instanceNameRoot = 'amy_is_testing'
		...
		regionName = 'us-east-1'
		amiId = 'ami-ed550784'

0. Launch the EC2 instances using the H2O AMI by running `h2o-cluster-launch-instances.py`. 

		$ python h2o-cluster-launch-instances.py
			Using boto version 2.27.0
			Launching 2 instances.
			Waiting for instance 1 of 2 ...
			  .
			  .
			  instance 1 of 2 is up.
			Waiting for instance 2 of 2 ...
			  instance 2 of 2 is up.

		Creating output files:  nodes-public nodes-private

		Instance 1 of 2
		  Name:    amy_is_testing0
		  PUBLIC:  ec2-54-164-161-125.compute-1.amazonaws.com
		  PRIVATE: 172.31.21.154

		Instance 2 of 2
		  Name:    amy_is_testing1
		  PUBLIC:  ec2-54-164-161-149.compute-1.amazonaws.com
		  PRIVATE: 172.31.21.155

		Sleeping for 60 seconds for ssh to be available...
		Testing ssh access ...

		Distributing flatfile...

0. Download the latest build of H2O-Dev onto each instance using 

   - `./h2o-cluster-distribute-h2o.sh`

       or
    
   - `./h2o-cluster-download-h2o.sh` 

  The second method is usually faster, since the file is downloaded from S3.
  
  ```
  $ ./h2o-cluster-download-h2o.sh
Fetching latest build number for branch master...
Fetching full version number for build 1480...
Downloading H2O version 2.7.0.1480 to cluster...
Downloading h2o.jar to node 1: ec2-54-164-161-125.compute-1.amazonaws.com
Downloading h2o.jar to node 2: ec2-54-164-161-149.compute-1.amazonaws.com
Warning: Permanently added 'ec2-54-164-161-125.compute-1.amazonaws.com,54.164.161.125'
(RSA) to the list of known hosts.
Warning: Permanently added 'ec2-54-164-161-149.compute-1.amazonaws.com,54.164.161.149'
(RSA) to the list of known hosts.
Unzipping h2o.jar within node 1: ec2-54-164-161-125.compute-1.amazonaws.com
Unzipping h2o.jar within node 2: ec2-54-164-161-149.compute-1.amazonaws.com
Copying h2o.jar within node 1: ec2-54-164-161-125.compute-1.amazonaws.com
Copying h2o.jar within node 2: ec2-54-164-161-149.compute-1.amazonaws.com
Success.
```


0. Distribute a `flatfile.txt` that contains all the private node IP addresses. 

		$ ./h2o-cluster-distribute-flatfile.sh
		Copying flatfile to node 1: ec2-54-164-161-125.compute-1.amazonaws.com
		flatfile.txt                             100%   40     0.0KB/s   00:00
		Copying flatfile to node 2: ec2-54-164-161-149.compute-1.amazonaws.com
		flatfile.txt                             100%   40     0.0KB/s   00:00
		Success.


0. (Optional) To import data from a private S3 bucket, enable permissions on each launched node. If the cluster was launched without an IAM profile and policy, then AWS credentials must be distributed to each node as an `aws_credentials.properties` file using `./ho2-cluster-distribute-aws-crednetials.sh`. If the cluster was launched with an IAM profile, H2O detect the temporary credentials on the cluster. 

		$ ./h2o-cluster-distribute-aws-credentials.sh
		Copying aws credential files to node 1: ec2-54-164-161-125.compute-1.amazonaws.com
		core-site.xml                              100%  500     0.5KB/s   00:00
		aws_credentials.properties                 100%   82     0.1KB/s   00:00
		Copying aws credential files to node 2: ec2-54-164-161-149.compute-1.amazonaws.com
		core-site.xml                              100%  500     0.0KB/s   00:17
		aws_credentials.properties                 100%   82     0.1KB/s   00:00
		Success.


0. To launch H2O, use `./h2o-cluster-start-h2o.sh`.

		$ h2o-cluster-start-h2o.sh
		Starting on node 1: ec2-54-164-161-125.compute-1.amazonaws.com...
		JAVA_HOME is ./jdk1.7.0_40
		java version "1.7.0_40"
		Java(TM) SE Runtime Environment (build 1.7.0_40-b43)
		Java HotSpot(TM) 64-Bit Server VM (build 24.0-b56, mixed mode)
		01:55:18.438 main      INFO WATER: ----- H2O started -----
		01:55:18.632 main      INFO WATER: Build git branch: master
		01:55:18.633 main      INFO WATER: Build git hash: 1fbeb98671c73d4e2a61fc3defecb6bd1646c4d5
		01:55:18.633 main      INFO WATER: Build git describe: nn-2-9356-g1fbeb98
		01:55:18.634 main      INFO WATER: Build project version: 2.7.0.1480
		01:55:18.634 main      INFO WATER: Built by: 'jenkins'
		01:55:18.635 main      INFO WATER: Built on: 'Thu Aug 21 23:51:30 PDT 2014'
		01:55:18.635 main      INFO WATER: Java availableProcessors: 1
		01:55:18.649 main      INFO WATER: Java heap totalMemory: 0.01 gb
		01:55:18.649 main      INFO WATER: Java heap maxMemory: 0.14 gb
		01:55:18.650 main      INFO WATER: Java version: Java 1.7.0_40 (from Oracle Corporation)
		01:55:18.651 main      INFO WATER: OS   version: Linux 2.6.32-358.14.1.el6.x86_64 (amd64)
		01:55:18.959 main      INFO WATER: Machine physical memory: 0.58 gb
		Starting on node 2: ec2-54-164-161-149.compute-1.amazonaws.com...
		JAVA_HOME is ./jdk1.7.0_40
		java version "1.7.0_40"
		Java(TM) SE Runtime Environment (build 1.7.0_40-b43)
		Java HotSpot(TM) 64-Bit Server VM (build 24.0-b56, mixed mode)
		01:55:21.983 main      INFO WATER: ----- H2O started -----
		01:55:22.067 main      INFO WATER: Build git branch: master
		01:55:22.068 main      INFO WATER: Build git hash: 1fbeb98671c73d4e2a61fc3defecb6bd1646c4d5
		01:55:22.068 main      INFO WATER: Build git describe: nn-2-9356-g1fbeb98
		01:55:22.069 main      INFO WATER: Build project version: 2.7.0.1480
		01:55:22.069 main      INFO WATER: Built by: 'jenkins'
		01:55:22.069 main      INFO WATER: Built on: 'Thu Aug 21 23:51:30 PDT 2014'
		01:55:22.070 main      INFO WATER: Java availableProcessors: 1
		01:55:22.082 main      INFO WATER: Java heap totalMemory: 0.01 gb
		01:55:22.082 main      INFO WATER: Java heap maxMemory: 0.14 gb
		01:55:22.083 main      INFO WATER: Java version: Java 1.7.0_40 (from Oracle Corporation)
		01:55:22.084 main      INFO WATER: OS   version: Linux 2.6.32-358.14.1.el6.x86_64 (amd64)
		01:55:22.695 main      INFO WATER: Machine physical memory: 0.58 gb
		Success.



