##
## AWS Managed Policies
##
data "aws_iam_policy" "AmazonElasticMapReduceRole" {
  arn = "arn:aws:iam::aws:policy/service-role/AmazonElasticMapReduceRole"
}

data "aws_iam_policy_document" "emr_assume_role_policy" {
  statement {
    effect = "Allow"
    actions = [
      "sts:AssumeRole"]
    principals {
      type = "Service"
      identifiers = [
        "elasticmapreduce.amazonaws.com"]
    }
  }
}

##
## Create Role with Assume Policy for EMR
##
resource "aws_iam_role" "emr_role" {
  path = "/"
  assume_role_policy = "${data.aws_iam_policy_document.emr_assume_role_policy.json}"
}

##
## Attach policy to the created role
##
resource "aws_iam_role_policy_attachment" "emr_role_policy_attach" {
  role = "${aws_iam_role.emr_role.name}"
  policy_arn = "${data.aws_iam_policy.AmazonElasticMapReduceRole.arn}"
}
