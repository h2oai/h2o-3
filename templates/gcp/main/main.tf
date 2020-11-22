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
