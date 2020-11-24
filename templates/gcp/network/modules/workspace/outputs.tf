output "workspace_instance_name" {
  value = google_compute_instance.workspace.name 
}
output "workspace_instance_id" {
  value = google_compute_instance.workspace.instance_id 
}
output "workspace_instance_internal_ip" {
  value = google_compute_instance.workspace.network_interface[0].network_ip
}
output "workspace_instance_external_ip" {
  value = google_compute_instance.workspace.network_interface[0].access_config[0].nat_ip 
}
