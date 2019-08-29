##
## Output variables - used or created resources
##
output "flow_url" {
  value = "${module.emr.flow_url}"
}
output "bucket" {
  value = "${module.emr.bucket}"
}
