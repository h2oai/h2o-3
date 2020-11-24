# Variables with defaults
#
# A common prefix for all resources created by this terraform script
variable "global_prefix" {
  type = string
}

#
# google provider variables
#
variable "gcp_project_id" {
  type = string
}
variable "gcp_project_region" {
  type = string
}
variable "gcp_project_zone" {
  type = string
}

#
# VPC variables
#
variable "vpc_private_subnet_id" {
  type = string
}

#
# H2O cluster instance variables
#
variable h2o_cluster_instance_count {
  type = string
}
variable h2o_cluster_instance_boot_disk_image {
  type = string
}
variable h2o_cluster_instance_boot_disk_type {
  type = string
}
variable h2o_cluster_instance_boot_disk_size {
  type = string
}
variable h2o_cluster_instance_machine_type {
  type = string
}
# Variables for which input is captured from user
variable h2o_cluster_instance_user {
  type = string
}
variable h2o_cluster_random_string {
  type = string
}
variable h2o_cluster_instance_description {
  type = string
}
