##
## Output variables - used or created resources
##
output "flow_url" {
  value = "https://${aws_alb.alb_front.dns_name}:54321/"
}
output "bucket" {
  value = "${format("s3://%s", aws_s3_bucket.h2o_bucket.bucket)}"
}
