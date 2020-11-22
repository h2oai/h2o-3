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
variable instance_boot_disk_image {
  type = string
  default = "rhel-cloud/rhel-7"
}
variable instance_boot_disk_type {
  type = string
  default = "pd-ssd"
}
variable instance_boot_disk_size {
  type = string
  default = 256
}
variable instance_machine_type {
  type = string
  default = "e2-medium"
}
