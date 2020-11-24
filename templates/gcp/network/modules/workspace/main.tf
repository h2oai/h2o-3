
resource "google_compute_instance" "workspace" {
  name = var.instance_name
  description = var.instance_description
  project = var.instance_project
  machine_type = var.instance_machine_type
  
  boot_disk {
    initialize_params {
      image = var.instance_boot_disk_image
      type = var.instance_boot_disk_type
      size = var.instance_boot_disk_size
    }
  }
  
  zone = var.instance_zone
  network_interface {
    subnetwork = var.instance_subnet
    # to make instance private do not include an access_config structure in network_interface structure
    access_config {}
  }
 
  # For firewall rules later
  tags = ["h2o-workspace", "public"]
  
  # Will use OS Login to the Workspace
  metadata = {
    enable-oslogin = "TRUE"
  }
  
  
  
  # Startup script in the server
  metadata_startup_script = file("${path.module}/startup.sh")
}

