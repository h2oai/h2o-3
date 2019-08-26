##
## Output variables - used or created resources
##
output "master_public_dns" {
  value = "${module.emr.master_public_dns}"
}
output "flow_url" {
  value = "https://${module.emr.master_public_dns}:54321/"
}
output "bucket" {
  value = "${module.emr.bucket}"
}
