#
# Variables requiring runtime input
#
# username is used in the random name for cluster group and instances
variable "username" {
  type = string
}

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
variable "gcp_project_name" {
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
