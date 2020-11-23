#
# Variables with defaults
#
# A common prefix for all resources created by this terraform script
variable "global_prefix" {
  type = string
  default = "h2o"
}


#
# google provider variables
#
variable "gcp_project_id" {
  type = string
  default = "steamwithdataproc"
}
variable "gcp_project_region" {
  type = string
  default = "us-west1"
}
variable "gcp_project_zone" {
  type = string
  default = "us-west1-a"
}

#
# VPC variables
#
variable "vpc_cidr" {
  type = string
  default = "10.100.0.0/16"
}
variable "vpc_subnet_private_cidr" {
  type = string
  default = "10.100.1.0/24"
}
variable "vpc_subnet_public_cidr" {
  type = string
  default = "10.100.200.0/24"
}


#
# Workspace instance variables
#
variable workspace_instance_boot_disk_image {
  type = string
  default = "rhel-cloud/rhel-7"
}
variable workspace_instance_boot_disk_type {
  type = string
  default = "pd-ssd"
}
variable workspace_instance_boot_disk_size {
  type = string
  default = 256
}
variable workspace_instance_machine_type {
  type = string
  default = "e2-medium"
}

#
# H2O cluster instance variables
#
variable h2o_cluster_instance_count {
  type = string
  default = "3"
}
variable h2o_cluster_instance_boot_disk_image {
  type = string
  default = "rhel-cloud/rhel-7"
}
variable h2o_cluster_instance_boot_disk_type {
  type = string
  default = "pd-ssd"
}
variable h2o_cluster_instance_boot_disk_size {
  type = string
  default = 30 
}
variable h2o_cluster_instance_machine_type {
  type = string
  default = "e2-highmem-4"
}
# Variables for which input is captured from user
variable h2o_cluster_instance_user {
  type = string
  description = "Provide Username to use in cluster name (no space)"
}
variable h2o_cluster_random_string {
  type = string
  description = "Provide random string to user in cluster name (min 5 characters, only letters)"
}
variable h2o_cluster_instance_description {
  type = string
  description = "Provide description for the h2o cluster"
}
