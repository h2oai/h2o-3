##
## Provider definition
##
provider "aws" {
  region = "${var.aws_region}"
  access_key = "${var.aws_access_key}"
  secret_key = "${var.aws_secret_key}"
}

##
## VPC - if user did not specify aws_vpc_id
##
resource "aws_vpc" "main" {
  cidr_block = "${var.aws_vpc_cidr_block}"
  enable_dns_hostnames = true
  enable_dns_support = true
  enable_classiclink = false
  enable_classiclink_dns_support = false

  tags = {
    Name = "H2ODeployment"
  }
}


##
## VPC Subnet
##
resource "aws_subnet" "main" {
  vpc_id = "${aws_vpc.main.id}"
  cidr_block = "${var.aws_subnet_cidr_block}"
  availability_zone = "${var.aws_availability_zone}"
  tags = {
    name = "H2ODeploymentSubnet"
  }
}

##
## Internat Gateway
##
resource "aws_internet_gateway" "gw" {
  vpc_id = "${aws_vpc.main.id}"

  tags = {
    Name = "H2ODeploymentGateway"
  }
}

##
## Route table (mainly for gataway)
## 
resource "aws_route_table" "r" {
  vpc_id = "${aws_vpc.main.id}"

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = "${aws_internet_gateway.gw.id}"
  }
}

##
## Associate the route table with the VPC
##
resource "aws_main_route_table_association" "a" {
  vpc_id = "${aws_vpc.main.id}"
  route_table_id = "${aws_route_table.r.id}"
}

##
## Create DHCP Options
##
resource "aws_vpc_dhcp_options" "main" {
  domain_name = "ec2.internal"
  domain_name_servers = [
    "AmazonProvidedDNS"]
  tags = {
    Name = "H2ODeploymentDHCPOptions"
  }
}

##
## Associate DHCP options with the VPC
##
resource "aws_vpc_dhcp_options_association" "dns_resolver" {
  vpc_id = "${aws_vpc.main.id}"
  dhcp_options_id = "${aws_vpc_dhcp_options.main.id}"
}
