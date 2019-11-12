resource "aws_security_group" "master" {
  description = "Security group for master node"
  vpc_id = "${data.aws_vpc.main.id}"
  revoke_rules_on_delete = true
  ingress {
    from_port = 54321
    to_port = 54321
    protocol = "tcp"
    cidr_blocks = [
      "0.0.0.0/0"]
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = [
      "0.0.0.0/0"]
  }
  depends_on = [
    "data.aws_subnet.main"]
}


resource "aws_security_group" "slave" {
  description = "Security group for worker node"
  vpc_id = "${data.aws_vpc.main.id}"
  revoke_rules_on_delete = true
  ingress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    security_groups = ["${aws_security_group.master.id}"]
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = [
      "0.0.0.0/0"]
  }
  depends_on = [
    "data.aws_subnet.main"]
}

resource "aws_security_group" "service" {
  description = "Security group for service access"
  vpc_id = "${data.aws_vpc.main.id}"
  revoke_rules_on_delete = true
  ingress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = [
      "0.0.0.0/0"]
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = [
      "0.0.0.0/0"]
  }
  depends_on = [
    "data.aws_subnet.main"]
}
