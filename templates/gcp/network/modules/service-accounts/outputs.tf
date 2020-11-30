output "workspace-vm-sa-email" {
  value = google_service_account.workspace-vm-sa.email 
}
output "h2o-cluster-vm-sa-email" {
  value = google_service_account.h2o-cluster-vm-sa.email
}

