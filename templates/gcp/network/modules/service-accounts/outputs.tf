output "workspace-vm-sa-email" {
  value = google_service_account.workspace-vm-sa.email 
}
output "h2ocluster-vm-sa-email" {
  value = google_service_account.h2ocluster-vm-sa.email
}

