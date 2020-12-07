terraform {
  required_version = ">= 0.13.0, < 0.14.0"
  required_providers {
    google = {
      source = "hashicorp/google"
      version = "3.48.0"
    }
  }
}

provider "google" {
  project = var.gcp_project_id
  region = var.gcp_project_region
  zone = var.gcp_project_zone
}
