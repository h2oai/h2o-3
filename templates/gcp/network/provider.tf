terraform {
  required_version = ">= 0.13.0"
  required_providers {
    google = {
      source = "hashicorp/google"
      version = "3.48.0"
    }
  }
  backend "gcs" {
    bucket = "steamwithdataproc-tfstate"
    prefix = "h2o/terraform"
  }
}

provider "google" {
  project = var.gcp_project_id
  region = var.gcp_project_region
  zone = var.gcp_project_zone
}
