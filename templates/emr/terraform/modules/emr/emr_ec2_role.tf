##
## AWS Managed Policies
##
data "aws_iam_policy" "AmazonElasticMapReduceforEC2Role" {
  arn = "arn:aws:iam::aws:policy/service-role/AmazonElasticMapReduceforEC2Role"
}

data "aws_iam_policy_document" "ec2_assume_role_policy" {
  statement {
    effect = "Allow"
    actions = [
      "sts:AssumeRole"]
    principals {
      type = "Service"
      identifiers = [
        "ec2.amazonaws.com"]
    }
  }
}

##
## Create Role with Assume Policy for EMR
##
resource "aws_iam_role" "emr_ec2_role" {
  path = "/"
  assume_role_policy = "${data.aws_iam_policy_document.ec2_assume_role_policy.json}"
}

##
## Attach policy to the created role
##
resource "aws_iam_role_policy_attachment" "emr_for_ec2_role_policy_attach" {
  role = "${aws_iam_role.emr_ec2_role.name}"
  policy_arn = "${data.aws_iam_policy.AmazonElasticMapReduceforEC2Role.arn}"
}

##
## Create Instance Profile 
##
resource "aws_iam_instance_profile" "emr_ec2_instance_profile" {
  role = "${aws_iam_role.emr_ec2_role.name}"
}
