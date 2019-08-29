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

resource "aws_subnet" "public" {
  vpc_id = "${aws_vpc.main.id}"
  cidr_block = "${var.aws_subnet_public_cidr_block}"
  availability_zone = "${var.aws_availability_zone}"
  map_public_ip_on_launch = "true"
  tags = {
    name = "H2ODeploymentSubnetPublic"
  }
}

resource "aws_subnet" "public2" {
  vpc_id = "${aws_vpc.main.id}"
  cidr_block = "${var.aws_subnet_public2_cidr_block}"
  availability_zone = "${var.aws_availability_zone2}"
  map_public_ip_on_launch = "true"
  tags = {
    name = "H2ODeploymentSubnetPublic2"
  }
}

resource "aws_subnet" "main" {
  vpc_id = "${aws_vpc.main.id}"
  cidr_block = "${var.aws_subnet_main_cidr_block}"
  availability_zone = "${var.aws_availability_zone}"
  tags = {
    name = "H2ODeploymentSubnetMain"
  }
  depends_on = ["aws_subnet.public"]
}

##
## Internet Gateway
##
resource "aws_internet_gateway" "gw" {
  vpc_id = "${aws_vpc.main.id}"
}

resource "aws_eip" "nat" {
  vpc = true
}

resource "aws_nat_gateway" "gw" {
  allocation_id = "${aws_eip.nat.id}"
  subnet_id = "${aws_subnet.public.id}"

  tags = {
    Name = "H2ODeploymentGateway"
  }

  depends_on = ["aws_internet_gateway.gw", "aws_subnet.public", "aws_subnet.public2"]
}

##
## Route table (mainly for gateway)
## 
resource "aws_route_table" "r" {
  vpc_id = "${aws_vpc.main.id}"

  route {
    cidr_block = "0.0.0.0/0"
    nat_gateway_id = "${aws_nat_gateway.gw.id}"
  }
}

resource "aws_route_table" "r-public" {
  vpc_id = "${aws_vpc.main.id}"
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = "${aws_internet_gateway.gw.id}"
  }
}

resource "aws_route_table_association" "a-public" {
  subnet_id = "${aws_subnet.public.id}"
  route_table_id = "${aws_route_table.r-public.id}"
}

resource "aws_route_table_association" "a-public2" {
  subnet_id = "${aws_subnet.public2.id}"
  route_table_id = "${aws_route_table.r-public.id}"
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
