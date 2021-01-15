import requests
import argparse
import sys
import os
import time

from kubernetes import client, config, watch


def wait_deployment_ready(deployment_name: str, namespace: str) -> client.V1Deployment:
    v1_apps = client.AppsV1Api()
    w = watch.Watch()
    for deployment in w.stream(v1_apps.list_namespaced_deployment, namespace,
                               field_selector="metadata.name={}".format(deployment_name), _request_timeout=360):
        deployment = deployment["object"]
        status: client.V1DeploymentStatus = deployment.status
        if status.ready_replicas == status.replicas:
            return deployment


def create_cluster(deployment_name: str, namespace: str) -> [str]:
    config.load_kube_config(config_file=os.getenv("KUBECONFIG"))
    deployment = wait_deployment_ready(deployment_name, namespace)
    print(deployment)
    return cluster_deployment_pods(deployment, namespace)


def cluster_deployment_pods(deployment: client.V1Deployment, namespace: str) -> [str]:
    pod_label = deployment.spec.selector.match_labels["app"];
    pod_ips = get_pod_ips_by_label(pod_label, namespace)
    print("Detected pod_ips: {}".format(pod_ips))
    send_ips_to_pods(pod_ips)
    return pod_ips


def get_deployment(deployment_name: str, namespace: str) -> client.V1Deployment:
    v1_apps_api = client.AppsV1Api()
    deployment = v1_apps_api.read_namespaced_deployment(deployment_name, namespace)
    if deployment is None:
        print("Deployment '{}' does not exist".format(deployment_name))
        sys.exit(1)
    else:
        return deployment


def send_ips_to_pods(pod_ips):
    flatfile_body = ""
    for i in range(len(pod_ips)):
        if i == len(pod_ips) - 1:
            flatfile_body += "{}:54321".format(pod_ips[i])  # no \n after last flatfile record
        else:
            flatfile_body += "{}:54321\n".format(pod_ips[i])

    for pod_ip in pod_ips:
        url = "http://{}:8080/clustering/flatfile".format(pod_ip)
        headers = {"accept": "*/*",
                   "Content-Type": "text/plain"}
        response = requests.post(url, headers=headers, data=flatfile_body)
        if response.status_code != 200:
            print("Unexpected response code from pod '{}'")
            sys.exit(1)


def check_h2o_clustered(pod_ips):
    for pod_ip in pod_ips:
        url = "http://{}:8080/cluster/status".format(pod_ip)

        response = None
        max_retries = 360
        retries = 0
        while retries < max_retries:
            response = requests.get(url)
            if response.status_code == 200:
                break
            time.sleep(1)

        if response is None:
            print("Unable to obtain /cluster/status response from pod '{}' in time.".format(pod_ip))
            sys.exit(1)

        response_json = response.json()
        if len(response_json["unhealthy_nodes"]) > 0:
            print("Unhealthy nodes detected in the cluster: {}".format(response_json["unhealthy_nodes"]))
            sys.exit(1)

        if len(response_json["healthy_nodes"]) != len(pod_ips):
            print("Healthy cluster with less node reported by node {}. IPs: {}".format(pod_ip,
                                                                                         response_json[
                                                                                             "healthy_nodes"]))
            sys.exit(1)
            
        print("Pod {} reporting healthy cluster:\n{}".format(pod_ip, response_json))

def get_pod_ips_by_label(pod_label: str, namespace: str):
    v1_core_pi = client.CoreV1Api();
    pods = v1_core_pi.list_namespaced_pod(watch=False, namespace=namespace, label_selector="app={}".format(pod_label),
                                          _request_timeout=360)
    pod_ips = list()
    for pod in pods.items:
        pod_ips.append(pod.status.pod_ip)

    return pod_ips


if __name__ == '__main__':
    args = argparse.ArgumentParser("H2O Assisted clustering test script")
    args.add_argument("deployment_name", help="Name of the H2O Deployment in K8S",
                      metavar="L", type=str)
    args.add_argument("--namespace", required=True, help="Namespace the H2O has been deployed to",
                      type=str)
    parsed_args = args.parse_args()
    deployment_name, namespace = parsed_args.deployment_name, parsed_args.namespace
    
    pod_ips = create_cluster(deployment_name, namespace)
    check_h2o_clustered(pod_ips)
