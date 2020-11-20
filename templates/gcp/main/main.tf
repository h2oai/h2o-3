module "vpc" {
  source = "../modules/vpc"
  
  gcp_project_id = var.gcp_project_id
  vpc_name = "${var.global_prefix}-vpc"
  vpc_region = var.gcp_project_region
}
