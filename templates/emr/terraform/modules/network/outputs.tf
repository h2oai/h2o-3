##
## Output variables
##

output "aws_vpc_id" {
  value = "${aws_vpc.main.id}"
}

output "aws_subnet_id" {
  value = "${aws_subnet.main.id}"
}

output "aws_subnet_public_id" {
  value = "${aws_subnet.public.id}"
}

output "aws_subnet_public2_id" {
  value = "${aws_subnet.public2.id}"
}

output "aws_vpc_cidr_block" {
  value = "${aws_vpc.main.cidr_block}"
}

output "aws_subnet_cidr_block" {
  value = "${aws_subnet.main.cidr_block}"
}

output "aws_region" {
  value = "${var.aws_region}"
}

