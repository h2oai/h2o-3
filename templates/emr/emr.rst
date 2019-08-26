Deployment template for EMR
---------------------------

**Note**: Both the deployment template and this documentation is currently work-in-progress.

Introduction
~~~~~~~~~~~~

We provide an example of a template that can be used to create a new AWS EMR cluster and start a secured H2O cluster.
The template is meant as a starting point and users are expected to customize it.

Using the template
~~~~~~~~~~~~~~~~~~

1. Download and install HashiCorm Terraform: https://www.terraform.io/
2. Navigate your terminal to directory templates/emr/terraform of the H2O-3 repository
3. Run `terraform init`
4. Create a new EMR cluster by running `terraform apply`
    - this command will ask you for your AWS access key and secret key
    - it will setup the security groups and networking needed to run an H2O cluster (review the configuration in `modules/network`)
    - it will create a 2 node H2O cluster using `m5.xlarge` instance, type and number of instances used can be changed in `modules/emr/variables.tf`)
    - H2O Flow will be exposed on the master node of the cluster on port 54321, Flow will be password protected - use username hadoop and password hadoop123 to login. 
