# dont change value of global_prefix.
global_prefix = "h2o"

gcp_project_id = "steamwithdataproc"
gcp_project_region = "us-west1"
gcp_project_zone = "us-west1-a"

vpc_private_subnet_id = " https://www.googleapis.com/compute/v1/projects/steamwithdataproc/regions/us-west1/subnetworks/h2o-vpc-private-subnet"

h2o_cluster_instance_count = "3"
h2o_cluster_instance_machine_type = "e2-highmem-4"
h2o_cluster_instance_boot_disk_image = "rhel-cloud/rhel-7"
h2o_cluster_instance_boot_disk_type = "pd-ssd"
h2o_cluster_instance_boot_disk_size  = 30

h2o_cluster_instance_service_account_email = "h2ocluster-vm-sa@steamwithdataproc.iam.gserviceaccount.com"
h2o_cluster_instance_service_account_scopes = ["cloud-platform"]

h2o_download_url = "http://h2o-release.s3.amazonaws.com/h2o/rel-zermelo/2/h2o-3.32.0.2.zip"
