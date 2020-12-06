#!/usr/bin/env bash

# Set script options
# https://kvz.io/bash-best-practices.html
# https://tldp.org/LDP/abs/html/options.html
set -o errexit          # exit script when any command fails
# set -o nounset          # exit if using undefined variables
set -o pipefail         # exit and return the exist status of last command in pipe to fail
set -o xtrace           # Uncomment to enable script debugging

# Get important execution information
# shellcheck disable=SC2034
exec_dir=$(pwd)
# shellcheck disable=SC2034
script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# shellcheck disable=SC2034
script_name=$(basename "${0}")

# Setup directories for state management
h2ocluster_home="${HOME}/.h2ocluster"
h2ocluster_info_file="${h2ocluster_home}/clusterinfo"
# Terraform data and state directories
h2ocluster_tfdata="${h2ocluster_home}/tfdata"
h2ocluster_tfstate="${h2ocluster_home}/tfstate"
mkdir -p "${h2ocluster_tfdata}"
mkdir -p "${h2ocluster_tfstate}"

# h2ocluster Terraform dir
h2ocluster_tfhome="/opt/h2ocluster"

# Initialize terraform for the first time
if [[ ! -f "${h2ocluster_tfdata}/plugins/registry.terraform.io/hashicorp/google/3.48.0/linux_amd64/terraform-provider-google_v3.48.0_x5" ]]; then
  pushd "${h2ocluster_tfhome}"
  TF_DATA_DIR="${h2ocluster_tfdata}" terraform init
  popd
fi

# Prefix for all clusters. Dont change this value
readonly prefix="h2o"

# Functions
error_exit () {
  echo ""
  echo "ERROR:"
  echo "    $1" 1>&2
  echo ""
  exit 1
}

print_heading () {
  echo ""
  echo "====== ${1} ======"
  echo ""
}

print_usage () {
  cat << EOUSAGE
USAGE:
    h2ocluster COMMAND [cluster_name] [--help]

POSSIBLE COMMANDS:
    list            Lists cluster_name for created H2O clusters
    create          Creates a new H2O cluster
    info            Shows information about a cluster
    start           Starts an already existing H2O cluster in stopped state
    stop            Stops an already existing H2O cluster in running steate
    destroy         Destroys an existing H2O cluster 

info, start, stop, and destroy, commands need cluster_name argument.

EOUSAGE
}

validate_cluster () {
  local cluster_name="${1}"
  grep -w "${cluster_name}" "${h2ocluster_info_file}" > /dev/null || { error_exit "Unknown cluster name: ${cluster_name}. Check using the list option." ; } 
}

exec_action () {
  local action="${1}"
  local cluster_name="${2}"
  case "${action}" in
    list )
        exec_list
        ;;
    create )
        exec_create
        ;;
    info )
        validate_cluster "${cluster_name}"
        exec_info "${cluster_name}"
        ;;
    start )
        validate_cluster "${cluster_name}"
        exec_start "${cluster_name}"
        ;;
    stop )
        validate_cluster "${cluster_name}"
        exec_stop "${cluster_name}"
        ;;
    destory )
        validate_cluster "${cluster_name}"
        exec_destroy "${cluster_name}"
        ;;
    esac
}

exec_list () {
  print_heading "LISTING CLUSTERS"
  (printf 'Cluster Name|Cluster Description\n'; \
   printf '============================|================================================\n'; \
   cat "${h2ocluster_info_file}") | column -t -s "|"
  echo -e "\n"
}

exec_create () {
  print_heading "CREATING CLUSTER"
  read -e -r -n 50 -p "Enter short description (max 50 chars): " cluster_description
  # shellcheck disable=SC2155
  local randstr=$(head /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 7 | head -n 1)
  local cluster_name="${prefix}-${USER}-${randstr}-cluster"
  echo -e "\n"
  echo -e "Creating H2O Cluster"
  echo -e "--------------------"
  echo -e "Cluster Name: ${cluster_name}" 
  echo -e "User: ${USER}" 
  echo -e "Description: ${cluster_description}" 
  echo -e "\n"
  # execute this as a single operation or return error 
  ( pushd /opt/h2ocluster && \
    TF_DATA_DIR="${h2ocluster_tfdata}" \
      terraform apply \
      -var "h2o_cluster_instance_user=${USER}" \
      -var "h2o_cluster_random_string=${randstr}" \
      -var "h2o_cluster_instance_description='${cluster_description}'" \
      -state="${h2ocluster_tfstate}/${cluster_name}.tfstate" && \
    popd && \
    echo "${cluster_name}|${cluster_description}" >> "${h2ocluster_info_file}" && \
    true ) || { error_exit "Cluster creation failed."; } 
}

exec_info () {
  local cluster_name="${1}"
  print_heading "CLUSTER INFORMATION"
  (printf 'Cluster Name|Cluster Description\n'; \
   printf '============================|================================================\n'; \
   grep -w "${cluster_name}" "${h2ocluster_info_file}" ) | column -t -s "|"
  echo -e "\n"
  echo -e "Cluster Instances:\n==================\n"
  gcloud compute instances list | grep -w "${cluster_name}"
}

exec_start () {
  echo "Start"
  echo "Cluster Name: ${1}"
}
exec_stop () {
  echo "Stop"
  echo "Cluster Name: ${1}"
}
exec_destroy() {
  local cluster_name="${1}"
  # shellcheck disable=SC2155,SC2116
  local randstr=$(echo "${cluster_name} | cut -d '-' -f 3")
  # shellcheck disable=SC2155
  local cluster_description=$(grep -w "${cluster_name}" "${h2ocluster_info_file}" | cut -d "|" -f 2)
  echo -e "\n"
  echo -e "Destroying H2O Cluster"
  echo -e "--------------------"
  echo -e "Cluster Name: ${cluster_name}" 
  echo -e "User: ${USER}" 
  echo -e "Description: ${cluster_description}" 
  echo -e "\n"
  # execute this as a single operation or return error 
  ( pushd /opt/h2ocluster && \
    TF_DATA_DIR="${h2ocluster_tfdata}" \
      terraform destroy \
      -var "h2o_cluster_instance_user=${USER}" \
      -var "h2o_cluster_random_string=${randstr}" \
      -var "h2o_cluster_instance_description='${cluster_description}'" \
      -state="${h2ocluster_tfstate}/${cluster_name}.tfstate" && \
    popd && \
    sed -i "/${cluster_name}/d" "${h2ocluster_info_file}" && \
    true ) || { error_exit "Cluster deletion failed."; } 
}


parse_args_and_exec () {
  local action=""
  local cluster_name=""
  [[ -n "${1}" ]] || { print_usage; error_exit "Missing expected parameters"; }
    while [[ "${1}" != "" ]]; do
      case "${1}" in
        list | create)
            action="${1}"
            cluster_name=""
            ;;
        info | start | stop | destroy )
            action="${1}"
            shift
            cluster_name="${1}"
            ;;
        --help )
            print_usage
            exit 0
            ;;
        * )
            print_usage
            error_exit "Unexpected parameters passed"
            ;;
      esac
      shift
    done
    exec_action "${action}" "${cluster_name}"
}


main () {
  parse_args_and_exec "$@"
}

main "$@"
