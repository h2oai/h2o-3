#
# google provider variables
#
variable "gcp_project_id" {
    type = string
}

#
# VPC variables
#
variable "vpc_name" {
    type = string
}
variable "vpc_region" {
    type = string
}
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
