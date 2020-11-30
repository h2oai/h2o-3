#
# Create the full VPC network
#
module "vpc" {
  source = "./modules/vpc"
  
  gcp_project_id = var.gcp_project_id

  vpc_name = "${var.global_prefix}-vpc"
  vpc_region = var.gcp_project_region

  vpc_cidr = var.vpc_cidr
  vpc_subnet_public_cidr = var.vpc_subnet_public_cidr
  vpc_subnet_private_cidr = var.vpc_subnet_private_cidr
}


# - Create a service account workspace-sa
# - Assign that service account and full scope to sorkspace VM
# - Create a service sccount h2ocluster-sa this will be assigned each time the h2oclusters are created
# - we can have many such service accounts to be assigned to h2oclusters created by appropriate group so that resources
#   accessible to that group are only accessible
# - All these SA will be managed from this network TF project
#
# Create Service Accounts
#
module "service-accounts" {
  source = "./modules/service-accounts"
  
  gcp_project_id = var.gcp_project_id 
}


#
# Create the Workspace instance
#
module "workspace-instance" {
  source = "./modules/workspace"
  
  instance_project = var.gcp_project_id
  
  instance_name = "${var.global_prefix}-instance-workspace"
  instance_description = "Workspace instance for H2O clusters"
  
  instance_zone = var.gcp_project_zone
  instance_subnet = module.vpc.vpc_public_subnet_id
  
  instance_machine_type = var.workspace_instance_machine_type
  instance_boot_disk_image = var.workspace_instance_boot_disk_image
  instance_boot_disk_type = var.workspace_instance_boot_disk_type
  instance_boot_disk_size = var.workspace_instance_boot_disk_size
 
  instance_service_account_email = module.service-accounts.workspace-vm-sa-email
  # Read - https://cloud.google.com/compute/docs/access/create-enable-service-accounts-for-instances#using
  # Scope List - https://cloud.google.com/sdk/gcloud/reference/alpha/compute/instances/set-scopes#--scopes
  instance_service_account_scopes = ["cloud-platform"] 
}

