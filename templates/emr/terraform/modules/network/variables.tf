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
variable "aws_availability_zone2" {
  default = "us-east-1a"
}
variable "aws_vpc_cidr_block" {
  default = "10.0.0.0/16"
}
variable "aws_subnet_main_cidr_block" {
  default = "10.0.0.0/24"
}
variable "aws_subnet_public_cidr_block" {
  default = "10.0.1.0/24"
}
variable "aws_subnet_public2_cidr_block" {
  default = "10.0.2.0/24"
}
