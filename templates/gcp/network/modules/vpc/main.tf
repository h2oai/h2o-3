
# Reference https://github.com/mohammadmonjureelahi/gke_eks_aks_terraform/blob/0a5c0674414d7578e6c3a5b97aac0137075ab5b8/GKE_HELM/modules/vpc-network/main.tf

#
# VPC configuration
#
resource "google_compute_network" "vpc" {
  name = var.vpc_name
  project = var.gcp_project_id
  description = "VPC for H2O clusters"
  
  # Do not create subnets automatically
  auto_create_subnetworks = "false"
  
  # Fixing all subnets to 1 region only
  routing_mode = "REGIONAL"
  
  # We want internet egress; so keep the default routes
  delete_default_routes_on_create = "false"
}

resource "google_compute_router" "vpc_router" {
  name = "${google_compute_network.vpc.name}-router"
  project = var.gcp_project_id
  
  region = var.vpc_region
  network = google_compute_network.vpc.self_link
}

resource "google_compute_router_nat" "vpc_router_nat" {
  name = "${google_compute_network.vpc.name}-router-nat"
  project = var.gcp_project_id
  region = google_compute_router.vpc_router.region 
 
  nat_ip_allocate_option = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "LIST_OF_SUBNETWORKS"
  router = google_compute_router.vpc_router.name
  
  subnetwork {
    name = google_compute_subnetwork.vpc_private_subnet.id 
    source_ip_ranges_to_nat = ["ALL_IP_RANGES"]
  }
}

#
# Public Subnet Configuration
#
resource "google_compute_subnetwork" "vpc_public_subnet" {
  name = "${google_compute_network.vpc.name}-public-subnet"
  project = var.gcp_project_id
  description = "Public Subnet to host the instance which will be used as worspace."

  region = var.vpc_region
  # ip_cidr_range = cidrsubnet(var.vpc_cidr, 8, 100)
  ip_cidr_range = var.vpc_subnet_public_cidr
  network = google_compute_network.vpc.self_link
}


#
# Private Subnet Configuration
#
resource "google_compute_subnetwork" "vpc_private_subnet" {
  name = "${google_compute_network.vpc.name}-private-subnet"
  project = var.gcp_project_id
  description = "Private Subnet within which the H2O clusters as instance groups will be created."
  
  region = var.vpc_region
  # ip_cidr_range = cidrsubnet(var.vpc_cidr, 8, 1)
  ip_cidr_range = var.vpc_subnet_private_cidr
  network = google_compute_network.vpc.self_link
 
  # Since this is private subnet we are enabling private access to google api resources on instances in this subnet
  private_ip_google_access = "true"
}


#
# Firewall rules
#

# For public instances 
resource "google_compute_firewall" "firewall_public_access" {
  name = "${google_compute_network.vpc.name}-firewall-public-access"
  project = var.gcp_project_id
  description = "Firewall rule for allowing access to public instances"

  # Rule is active
  disabled = false
  
  # For all instances in the VPC
  network = google_compute_network.vpc.self_link
  # for incoming traffic
  direction = "INGRESS"
  # allow ping and SSH 
  allow {
    protocol = "icmp"
  }
  allow {
    protocol = "tcp"
    ports = ["22"]
  }
  # from the internet
  source_ranges = ["0.0.0.0/0"] 
  # to instances that have the public tag
  target_tags = ["public"] 
}

# For private instances 
resource "google_compute_firewall" "firewall_private_access" {
  name = "${google_compute_network.vpc.name}-firewall-private-access"
  project = var.gcp_project_id
  description = "Firewall rule for allowing access to private instances"

  # Rule is active
  disabled = false

  # For all instances in the VPC
  network = google_compute_network.vpc.self_link
  # for incoming traffic
  direction = "INGRESS"
  # allow full access 
  allow {
    protocol = "icmp"
  }
  allow {
    protocol = "tcp"
  }
  allow {
    protocol = "udp"
  }
  # from the public subnet 
  source_ranges = [google_compute_subnetwork.vpc_public_subnet.ip_cidr_range, 
                   google_compute_subnetwork.vpc_private_subnet.ip_cidr_range]
  # to instances that have the public tag
  target_tags = ["private"]
}
