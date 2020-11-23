#
# Create the full VPC network
#
module "vpc" {
  source = "../modules/vpc"
  
  gcp_project_id = var.gcp_project_id

  vpc_name = "${var.global_prefix}-vpc"
  vpc_region = var.gcp_project_region

  vpc_cidr = var.vpc_cidr
  vpc_subnet_public_cidr = var.vpc_subnet_public_cidr
  vpc_subnet_private_cidr = var.vpc_subnet_private_cidr
  
}

#
# Create the Workspace instance
#
module "workspace-instance" {
  source = "../modules/workspace"
  
  instance_project = var.gcp_project_id
  
  instance_name = "${var.global_prefix}-instance-workspace"
  instance_description = "Workspace instance for H2O clusters"
  
  instance_zone = var.gcp_project_zone
  instance_subnet = module.vpc.vpc_public_subnet_id
  
  instance_machine_type = var.workspace_instance_machine_type
  instance_boot_disk_image = var.workspace_instance_boot_disk_image
  instance_boot_disk_type = var.workspace_instance_boot_disk_type
  instance_boot_disk_size = var.workspace_instance_boot_disk_size
}

#
# Create the H2O Cluster based on user input
# 
module "h2o-cluster" {
  source = "../modules/h2ocluster"
 
  # Organise in project
  h2o_cluster_instance_project = var.gcp_project_id
  
  # Components that form the customer name
  h2o_cluster_instance_name_prefix = var.global_prefix
  h2o_cluster_instance_user = var.h2o_cluster_instance_user
  h2o_cluster_instance_randstr = var.h2o_cluster_random_string
  # nodes in cluster
  h2o_cluster_instance_count = var.h2o_cluster_instance_count
  
  # Cluster Description
  h2o_cluster_instance_description = var.h2o_cluster_instance_description
 
  # Organise in Network
  h2o_cluster_instance_zone = var.gcp_project_zone
  h2o_cluster_instance_subnet = module.vpc.vpc_private_subnet_id
  
  # Cluster node instance information
  h2o_cluster_instance_machine_type = var.h2o_cluster_instance_machine_type
  h2o_cluster_instance_boot_disk_image = var.h2o_cluster_instance_boot_disk_image
  h2o_cluster_instance_boot_disk_type = var.h2o_cluster_instance_boot_disk_type
  h2o_cluster_instance_boot_disk_size = var.h2o_cluster_instance_boot_disk_size
}
