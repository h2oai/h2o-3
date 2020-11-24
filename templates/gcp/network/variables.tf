#
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
variable "vpc_cidr" {
  type = string
}
variable "vpc_subnet_private_cidr" {
  type = string
}
variable "vpc_subnet_public_cidr" {
  type = string
}


#
# Workspace instance variables
#
variable workspace_instance_boot_disk_image {
  type = string
}
variable workspace_instance_boot_disk_type {
  type = string
}
variable workspace_instance_boot_disk_size {
  type = string
}
variable workspace_instance_machine_type {
  type = string
}

