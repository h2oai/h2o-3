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


