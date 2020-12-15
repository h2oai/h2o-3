#
# Create the H2O Cluster based on user input
# 
module "h2o-cluster" {
  source = "./modules/h2ocluster"

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
  h2o_cluster_instance_subnet = var.vpc_private_subnet_id 

  # Cluster node instance information
  h2o_cluster_instance_machine_type = var.h2o_cluster_instance_machine_type
  h2o_cluster_instance_boot_disk_image = var.h2o_cluster_instance_boot_disk_image
  h2o_cluster_instance_boot_disk_type = var.h2o_cluster_instance_boot_disk_type
  h2o_cluster_instance_boot_disk_size = var.h2o_cluster_instance_boot_disk_size
  
  h2o_cluster_instance_service_account_email = var.h2o_cluster_instance_service_account_email
  h2o_cluster_instance_service_account_scopes = var.h2o_cluster_instance_service_account_scopes
  
  # h2o details
  h2o_download_url = var.h2o_download_url
}
