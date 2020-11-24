output "vpc_name" {
  value = module.vpc.vpc_name
}
output "vpc_id" {
  value = module.vpc.vpc_id 
}

output "vpc_public_subnet_name" {
  value = module.vpc.vpc_public_subnet_name 
}
output "vpc_public_subnet_id" {
  value = module.vpc.vpc_public_subnet_id
}
output "vpc_private_subnet_name" {
  value = module.vpc.vpc_private_subnet_name
}
output "vpc_private_subnet_id" {
  value = module.vpc.vpc_public_subnet_id
}

output "workspace_instance_name" {
  value = module.workspace-instance.workspace_instance_name 
}
output "workspace_instance_id" {
  value = module.workspace-instance.workspace_instance_id
}
output "workspace_instance_internal_ip" {
  value = module.workspace-instance.workspace_instance_internal_ip 
}
output "workspace_instance_external_ip" {
  value = module.workspace-instance.workspace_instance_external_ip
}



