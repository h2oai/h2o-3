output "vpc_name" {
  value = google_compute_network.vpc.name 
}
output "vpc_id" {
  value = google_compute_network.vpc.self_link
}

output "vpc_public_subnet_name" {
  value = google_compute_subnetwork.vpc_public_subnet.name
}
output "vpc_public_subnet_id" {
  value = google_compute_subnetwork.vpc_public_subnet.self_link
}
output "vpc_private_subnet_name" {
  value = google_compute_subnetwork.vpc_private_subnet.name
}
output "vpc_private_subnet_id" {
  value = google_compute_subnetwork.vpc_private_subnet.self_link
}


