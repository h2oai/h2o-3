# Service Account to be assigned to VM instances forming the h2o cluster
resource "google_service_account" "workspace-vm-sa" {
  project = var.gcp_project_id
  account_id = "workspace-vm-sa"
  display_name = "workspace-vm-sa"
  description = "Service account assigned to the Workspace VM"
}
# add IAM bindings for this SA. 
# Only 1 role per block is allowed. Add additional blocks for additional roles
resource "google_project_iam_member" "workspace-vm-sa-compute-admin-binding" {
  project = var.gcp_project_id
  role = "roles/compute.admin"
  member = "serviceAccount:${google_service_account.workspace-vm-sa.email}"
}
resource "google_project_iam_member" "workspace-vm-sa-serviceAccountUser-binding" {
  project = var.gcp_project_id
  role = "roles/iam.serviceAccountUser"
  member = "serviceAccount:${google_service_account.workspace-vm-sa.email}"
}




# Service Account to be assigned to VM instances forming the h2o cluster
resource "google_service_account" "h2ocluster-vm-sa" {
  project = var.gcp_project_id
  account_id = "h2ocluster-vm-sa"
  display_name = "h2ocluster-vm-sa"
  description = "Service account assigned to the VMs that form the H2O cluster"
}
# add IAM bindings for this SA. 
# Only 1 role per block is allowed. Add additional blocks for additional roles
resource "google_project_iam_member" "h2ocluster-vm-sa-serviceAccountUser-binding" {
  project = var.gcp_project_id
  role = "roles/iam.serviceAccountUser"
  member = "serviceAccount:${google_service_account.h2ocluster-vm-sa.email}"
}
resource "google_project_iam_member" "h2ocluster-vm-sa-compute-admin-binding" {
  project = var.gcp_project_id
  role = "roles/compute.admin"
  member = "serviceAccount:${google_service_account.h2ocluster-vm-sa.email}"
}
resource "google_project_iam_member" "h2ocluster-vm-storage-objectCreator-binding" {
  project = var.gcp_project_id
  role = "roles/storage.objectCreator"
  member = "serviceAccount:${google_service_account.h2ocluster-vm-sa.email}"
}
resource "google_project_iam_member" "h2ocluster-vm-storage-objectViewer-binding" {
  project = var.gcp_project_id
  role = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.h2ocluster-vm-sa.email}"
}
resource "google_project_iam_member" "h2ocluster-vm-bigquery-jobuser-binding" {
  project = var.gcp_project_id
  role = "roles/bigquery.jobUser"
  member = "serviceAccount:${google_service_account.h2ocluster-vm-sa.email}"
}
