Using H2O with Amazon AWS
~~~~~~~~~~~~~~~~~~~~~~~~~

AWS Marketplace provides a straightforward way to deploy a cluster of VMs with H2O.

1. Log in to the `AWS Marketplace <https://aws.amazon.com/marketplace/>`__. 

2. In the AMI & SaaS search bar, search for H2O, and select **H2O Artificial Intelligence** to open H2O in the marketplace. Review the information on this page. Note that the Delivery Methods section provides two options:

   - **H2O Cluster of VM**: All the VPC, subnets, and network security groups are created for you.
   - **H2O Cluster of VM - Bring your own VPC**: All the networks security groups, subnets, and internet gateways are created by the user.
   
   Click **Continue** after reviewing to open the Launch on EC2 page.

  .. figure:: ../images/aws_h2oai.png
      :alt: H2O Artificial Intelligence 

3. The Launch on EC2 page provides information about launch options. On the Manual Launch tab:

   - Select the offering that you prefer.
   - Select the region to launch.
   - Specify a Deployment option.

   Click **Launch with CloudFormation Console** to begin creating your stack.

  .. figure:: ../images/aws_launch_on_ec2.png
     :alt: Launch On EC2 page

4. On the Select Template page, review the template being used, follow the steps to deploy your cluster, and then click **Next** to continue.

  .. figure:: ../images/aws_select_template.png
     :alt: Select template

5. Enter any desired Stack details including the following:

   - Stack name (required)
   - InstanceType
   - KeyName
   - SSHLocation
   - vmCount
 
   Click **Next** to continue.

  .. figure:: ../images/aws_specify_details.png
     :alt: Specify details

6. Enter any optional tags and/or permissions on the Options page. Click **Next** to continue.

  .. figure:: ../images/aws_options.png
     :alt: Options page

7. Review the Stack configuration. Click **Create** to create the Stack, or click **Previous** to return to another page and edit any information.

After your cluster is created, you can access H2O Flow on any of the nodes by going to http://<public_dns>/:54321. Enter ``h2o`` for the username and your instance ID for the password. To connect to the operating system, use SSH and the username ``ubuntu``.
