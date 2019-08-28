##
## Input Variables
##
variable "aws_access_key" {}
variable "aws_secret_key" {}

variable "aws_region" {
  default = "us-east-1"
}
variable "aws_availability_zone" {
  default = "us-east-1e"
}
variable "aws_emr_version" {
  default = "emr-5.21.0"
}
variable "aws_core_instance_count" {
  default = "2"
}
variable "aws_instance_type" {
  default = "m5.xlarge"
}
variable "h2o_main_version" {
  default = "3.26.0"
}
variable "h2o_fix_version" {
  default = "2"
}
variable "h2o_codename" {
  default = "yau"
}
