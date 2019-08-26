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
variable "aws_vpc_cidr_block" {
  default = "10.0.0.0/16"
}
variable "aws_subnet_cidr_block" {
  default = "10.0.0.0/24"
}

