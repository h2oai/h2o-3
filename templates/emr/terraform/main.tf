module "network" {
  source = "./modules/network"
  aws_access_key = "${var.aws_access_key}"
  aws_secret_key = "${var.aws_secret_key}"
  aws_region = "${var.aws_region}"
  aws_availability_zone = "${var.aws_availability_zone}"
}


module "emr" {
  source = "./modules/emr"

  aws_access_key = "${var.aws_access_key}"
  aws_secret_key = "${var.aws_secret_key}"
  aws_region = "${var.aws_region}"
  certificate_arn = "${var.certificate_arn}"

  aws_vpc_id = "${module.network.aws_vpc_id}"
  aws_subnet_id = "${module.network.aws_subnet_id}"
  aws_subnet_public_id = "${module.network.aws_subnet_public_id}"
  aws_subnet_public2_id = "${module.network.aws_subnet_public2_id}"

  h2o_main_version = "${var.h2o_main_version}"
  h2o_fix_version = "${var.h2o_fix_version}"
  h2o_codename = "${var.h2o_codename}"
  
  h2o_mapper_xmx = "${var.h2o_mapper_xmx}"
  h2o_user_name = "${var.h2o_user_name}"

  aws_core_instance_count = "${var.aws_core_instance_count}"
  aws_instance_type = "${var.aws_instance_type}"
  aws_emr_version = "${var.aws_emr_version}"
}
