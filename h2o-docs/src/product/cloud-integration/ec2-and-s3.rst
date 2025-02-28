EC2 instances & S3 storage
==========================

*Tested on Redhat AMI, Amazon Linux AMI, and Ubuntu AMI*

To use the Amazon Web Services (AWS) S3 storage solution, you will need to pass your S3 access credentials to H2O. This will allow you to access your data on S3 when importing data frames with path prefixes ``s3://...``.

To use the `Minio Cloud Storage <https://minio.io/>`__, you will need to pass an endpoint in addition to access credentials.

For security reasons, we recommend writing a script to read the access credentials that are stored in a separate file. This will not only keep your credentials from propagating to other locations, but it will also make it easier to change the credential information later.

.. note::

 - You can only specify one S3 endpoint. This means you can either read data from AWS S3 or Minio S3, not from both.
 - We recommend using S3 for data ingestion and S3N for data export. 

AWS Standalone instance
-----------------------

When running H2O in standalone mode using the simple Java launch command, you can pass in the S3 credentials in three ways. H2O supports the following:

- AWS credentials: Pair consisting of AWS SECRET KEY and AWS SECRET ACCESS KEY.
- Session token (temporary authentication): Triplet consisting of AWS SECRET KEY, AWS SECRET ACCESS KEY and AWS SESSION TOKEN.

.. note::
  
  Passing credentials into the URL (e.g. ``h2o.importFile(path = "s3://<AWS_ACCESS_KEY>:<AWS_SECRET_KEY>:<AWS_SESSION_TOKEN>@bucket/path/to/file.csv")``) is deprecated and considered a security risk.

AWS credentials
~~~~~~~~~~~~~~~

You can pass in AWS Credentials in standalone mode by creating a ``core-site.xml`` file and passing it in with the flag ``-hdfs_config``. See `Core-site.xml`_ for an example ``core-site.xml`` file.

1. Edit the properties in the ``core-site.xml`` file to include your Access Key ID, Access Key, and Session Token:

  ::

    <property>
      <name>fs.s3.awsAccessKeyID</name>
      <value>[AWS SECRET KEY]</value>
    </property>

    <property>
      <name>fs.s3.awsSecretAccessKey</name>
      <value>[AWS SECRET ACCESS KEY]</value>
    </property>

2. Launch with the configuration file ``core-site.xml``:

  ::

    java -jar h2o.jar -hdfs_config core-site.xml

3. Set the credentials dynamically before accessing the bucket (where ``AWS_ACCESS_KEY`` represents your user name, and ``AWS_SECRET_KEY`` represents your password):

  .. tabs::
    .. code-tab:: python

      from h2o.persist import set_s3_credentials
      set_s3_credentials("AWS_ACCESS_KEY", "AWS_SECRET_KEY")
      h2o.import_file(path = "s3://bucket/path/to/file.csv")

    .. code-tab:: r R

      h2o.set_s3_credentials("AWS_ACCESS_KEY", "AWS_SECRET_KEY")
      h2o.importFile(path = "s3://bucket/path/to/file.csv")


Session token
~~~~~~~~~~~~~

Just like regular AWS credentials, temporary credentials using an AWS SESSION TOKEN can be passed in standalone mode by creating a ``core-site.xml`` file and passing it in with the flag ``-hdfs_config``. The only difference between the two lies in specifying a triplet of (AWS SECRET KEY, AWS SECRET ACCESS KEY and AWS SESSION TOKEN) and defining a credentials provider capable of resolving temporary credentials. See `Core-site.xml`_ for an example ``core-site.xml`` file. 

1. Edit the properties in the ``core-site.xml`` file to include your Access Key ID, Access Key, and Session Token:

  ::

    <property>
     <name>fs.s3a.aws.credentials.provider</name>
     <value>org.apache.hadoop.fs.s3a.TemporaryAWSCredentialsProvider</value>
    </property>

    <property>
     <name>fs.s3a.access.key</name>
     <value>[AWS SECRET KEY]</value>
    </property>

    <property>
     <name>fs.s3a.secret.key</name>
     <value>[AWS SECRET ACCESS KEY]</value>
    </property>

    <property>
     <name>fs.s3a.session.token</name>
     <value>[AWS SESSION TOKEN]<value>
    <property>

2. Launch with the configuration file ``core-site.xml``:

  ::

    java -jar h2o.jar -hdfs_config core-site.xml

3. Set he credentials dynamically before accessing the bucket (where AWS_ACCESS_KEY represents your user name, AWS_SECRET_KEY represents your password and AWS_SESSION_TOKEN represents temporary session token):

.. tabs::
  .. code-tab:: python

    from h2o.persist import set_s3_credentials
    set_s3_credentials("AWS_ACCESS_KEY", "AWS_SECRET_KEY", "AWS_SESSION_TOKEN")
    h2o.import_file(path = "s3://bucket/path/to/file.csv")

  .. code-tab:: r R

    h2o.set_s3_credentials("AWS_ACCESS_KEY", "AWS_SECRET_KEY", "AWS_SESSION_TOKEN")
    h2o.importFile(path = "s3://bucket/path/to/file.csv")

.. _Core-site.xml:

Core-site.xml example
~~~~~~~~~~~~~~~~~~~~~

The following is an example ``core-site.xml`` file:

::

    <?xml version="1.0"?>
    <?xml-stylesheet type="text/xsl" href="configuration.xsl"?>

    <!-- Put site-specific property overrides in this file. -->

    <configuration>

        <!--
        <property>
        <name>fs.default.name</name>
        <value>s3://<your s3 bucket></value>
        </property>
        -->

        <property>
            <name>fs.s3.awsAccessKeyId</name>
            <value>insert access key here</value>
        </property>

        <property>
            <name>fs.s3.awsSecretAccessKey</name>
            <value>insert secret key here</value>
        </property>

        <property>
            <name>fs.s3.awsSessionToken</name>
            <value>insert session token here</value>
        </property>
    </configuration>

AWS multi-node instance
-----------------------
`Python and the boto <http://boto.readthedocs.org/en/latest/>`_ Python library are required to launch a multi-node instance of H2O on EC2. Ensure these dependencies are installed before proceeding.

See the `H2O EC2 repo <https://github.com/h2oai/h2o-3/tree/master/ec2>`_ for more information.

Create an AWS multi-node instance
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Build a cluster of EC2 instances by running the following commands on the host that can access the nodes using a public DNS name.

1. Edit ``h2o-cluster-launch-instances.py`` to include your SSH key name and security group name (as well as any other environment-specific variables).

 ::

    ./h2o-cluster-launch-instances.py
    ./h2o-cluster-distribute-h2o.sh
    # -- OR --
    ./h2o-cluster-launch-instances.py
    ./h2o-cluster-download-h2o.sh

.. note:: 
  
  The second method may be faster than the first because download pulls from S3.

2. Distribute the credentials using ``./h2o-cluster-distribute-aws-credentials.sh``.

    .. note:: 
      
      If you are running H2O using an IAM role, it's not necessary to distribute the AWS credentials to all the nodes in the cluster. The latest version of H2O can access the temporary access key.

    .. warning::
      
      Distributing both regular AWS credentials and temporary AWS credentials using a session token copies the Amazon AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, and optionally (if temporary credentials are used) AWS_SESSION_TOKEN to the instances to enable S3 and S3N access. Use caution when adding your security keys to the cloud.

3. Start H2O by launching one H2O node per EC2 instance (wait 60 seconds after entering the command before entering it on the next node):

 ::

    ./h2o-cluster-start-h2o.sh

4. Substitute any of the public DNS node addresses for *IP_ADDRESS* in your internet browser for the following example: ``http://IP_ADDRESS:54321``

  - To start H2O: ``./h2o-cluster-start-h2o.sh``.
  - To stop H2O: ``./h2o-cluster-stop-h2o.sh``.
  - To shut down the cluster, use your `Amazon AWS console <http://docs.aws.amazon.com/ElasticMapReduce/latest/DeveloperGuide/UsingEMR_TerminateJobFlow.html>`_ to shut down the cluster manually.

  .. note:: 

    To successfully import data, the data must reside in the same location on all nodes.

.. _minio:

Minio instance
--------------

Minio Cloud Storage is an alternative to Amazon AWS S3. When connecting to a Minio server, the following additional parameters are specified in the Java launch command:

- ``endpoint``: Specifies a Minio server instance (including address and port). This overrides the existing endpoint, which is currently hardcoded to be AWS S3.

- ``enable.path.style``: Specifies to override the default S3 behavior to expose every bucket as a full DNS enabled path. Note that this is a Minio recommendation.

Create a Minio instance
~~~~~~~~~~~~~~~~~~~~~~~

To pass in credentials, create a ``core-site.xml`` file that contains your Access Key ID and Secret Access Key and use the flag ``-hdfs_config`` flag when launching:

::

       <property>
         <name>fs.s3.awsAccessKeyId</name>
         <value>[AWS SECRET KEY]</value>
       </property>

       <property>
         <name>fs.s3.awsSecretAccessKey</name>
         <value>[AWS SECRET ACCESS KEY]</value>
       </property>

1. Launch H2O by entering the following in the command line:

  ::

      java -Dsys.ai.h2o.persist.s3.endPoint=https://play.min.io:9000 -Dsys.ai.h2o.persist.s3.enable.path.style=true -jar h2o.jar -hdfs_config core-site.xml

  .. note:: 

    https://play.min.io:9000 is an example Minio server URL.

2. Import the data using ``importFile`` with the Minio S3 url path: ``s3://bucket/path/to/file.csv``.

.. tabs::
  .. code-tab:: bash Flow

    importFiles [ "s3://bucket/path/to/file.csv" ]

  .. code-tab:: python

    h2o.import_file(path = "s3://bucket/path/to/file.csv")

  .. code-tab:: r R

    h2o.importFile(path = "s3://bucket/path/to/file.csv")


Launch H2O
----------

.. note:: 

  Before launching H2O on an EC2 cluster, verify that ports ``54321`` and ``54322`` are both accessible by TCP.

Select the operating system and virtualization type
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Select your operating system and the virtualization type of the prebuilt AMI on Amazon. If you are using Windows, you will need to use a hardware-assisted virtual machine (HVM). If you are using Linux, you can choose between para-virtualization (PV) and HVM. These selections determine the type of instances you can launch.

.. figure:: ../EC2_images/ec2_system.png
   :alt: EC2 systems page where you select your amazon machine image.


See `Amazon virtualization <http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/virtualization_types.html>`__ for more information about virtualization types.


Configure the instance
~~~~~~~~~~~~~~~~~~~~~~

1. Select the IAM role and policy to use to launch the instance. H2O detects the temporary access keys associated with the instance, so you don't need to copy your AWS credentials to the instances.

  .. figure:: ../EC2_images/ec2_config.png
     :alt: EC2 page where you configure your instance details.

2. Select an accessible key pair when launching the instance.

  .. figure:: ../EC2_images/ec2_key_pair.png
     :alt: EC2 panel where you select an existing key pair or create a new key pair.


(Windows users) Tunnel into the instance
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For Windows users who do not have the ability to use ``ssh`` from the terminal, either download `Cygwin <https://www.cygwin.com/>`_ or a Git Bash that has the capability to run ``ssh``:

  ::

    ssh -i amy_account.pem ec2-user@54.165.25.98

PuTTY to tunnel into the instance
'''''''''''''''''''''''''''''''''

Otherwise, you can download `PuTTY <https://www.putty.org/>`_ and follow these instructions:

1. Launch the PuTTY Key Generator.
2. Load your downloaded AWS pem key file.

 .. note:: 

  To see the file, change the browser file type to "All".

3. Save the private key as a PPK file.

 .. figure:: ../EC2_images/ec2_putty_key.png
    :alt: PuTTy key generator with a PuTTYgen notice about successfully importing a foreign key.

4. Launch the PuTTY client.
5. Enter the host name or IP address in the *Session* section. For Ubuntu users, the default host name is ``ubuntu@<ip-address>``. For Linux users, the default host name is ``ec2-user@<ip-address>``.

 .. figure:: ../EC2_images/ec2_putty_connect_1.png
    :alt: PuTTY configuring session with default settings in the saved sessions section and the host name filled in.

6. Select *SSH*, then *Auth* in the sidebar, and click the **Browse** button to select the private key file for authentication.

 .. figure:: ../EC2_images/ec2_putty_connect_2.png
  :alt: PuTTY configuration with display pre-authentication banner and attempt authentication using Pageant selected.

7. Start a new session and click **Yes** to confirm caching of the server's rsa2 key fingerprint and continue connecting.

 .. figure:: ../EC2_images/ec2_putty_alert.png
    :alt: PuTTY security alert about the server's host key not cached in the registry.


Download Java and H2O
~~~~~~~~~~~~~~~~~~~~~

1. Download `Java <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/welcome.html#java-requirements>`__ (JDK 1.8 or later) if it is not already available on the instance.
2. Download H2O with the ``wget`` command and the link to the ZIP file available on our `website <http://h2o.ai/download/>`__ by copying the link associated with the **Download** button for the selected H2O build.

   ::

       wget http://h2o-release.s3.amazonaws.com/h2o/{{branch_name}}/{{build_number}}/index.html
       unzip h2o-{{project_version}}.zip
       cd h2o-{{project_version}}
       java -Xmx4g -jar h2o.jar

3. Navigate to ``<Private_IP_Address>:54321`` or ``<Public_DNS>:54321`` to use H2O's web interface from your browser.

