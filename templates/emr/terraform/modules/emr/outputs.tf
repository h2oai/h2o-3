##
## Output variables - used or created resources
##
output "master_public_dns" {
  value = "${aws_emr_cluster.h2o-cluster.master_public_dns}"
}
output "flow_url" {
  value = "https://${aws_emr_cluster.h2o-cluster.master_public_dns}:54321/"
}
output "bucket" {
  value = "${format("s3://%s", aws_s3_bucket.h2o_bucket.bucket)}"
}
