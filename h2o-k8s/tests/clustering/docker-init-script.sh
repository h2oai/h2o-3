#! /bin/bash -x

pwd
export H2O_BASE=$(pwd)
if [[ $string == *"@"* ]]; then
  echo "H2O base path contains at sign. Unable to create K3S cluster."
  exit 1
fi
cd $H2O_BASE/h2o-k8s/tests/clustering/
k3d --version
k3d delete
k3d create -v "$H2O_BASE":"$H2O_BASE" --registries-file registries.yaml --publish 8080:80 --api-port localhost:6444 --server-arg --tls-san="127.0.0.1" --wait 120
export KUBECONFIG="$(k3d get-kubeconfig --name='k3s-default')"
kubectl cluster-info
sleep 15 # Making sure the default namespace is initialized. The --wait flag does not guarantee this.
kubectl get namespaces
# Deploy H2O-3 Cluster as defined by Helm template in h2o-helm subproject
# Also tests correctness of the H2O HELM chart
envsubst < testvalues-template.yaml >> testvalues.yaml
helm install -f testvalues.yaml h2o $H2O_BASE/h2o-helm --kubeconfig $KUBECONFIG --dry-run # Shows resulting YAML
helm install -f testvalues.yaml h2o $H2O_BASE/h2o-helm --kubeconfig $KUBECONFIG
# Use helm built-in test pod
helm test h2o
# After the deployment, show status of H2O-related K8S resources
kubectl logs h2o-h2o-3-test-connection
kubectl get ingresses
kubectl describe pods
# Check H2O is clustered with a script independent from the one in h2o-helm triggered previously with 'helm test'
timeout 120s bash h2o-cluster-check.sh
export CLUSTER_EXIT_CODE=$?
kubectl get pods
kubectl get nodes
# To save resources in Jenkins pipeline (e.g. CPUs are limited), remove the H2O cluster deployed via HELM
helm uninstall h2o 
# Test assisted clustering regime by adding h2o-clustering artifact into the H2O container using volumes.
envsubst < h2o-assisted-template.yaml >> h2o-assisted.yaml
envsubst < h2o-python-clustering-template.yaml >> h2o-python-clustering.yaml
kubectl apply -f h2o-assisted.yaml
# First, wait for H2O Pods to be ready
kubectl wait --timeout=180s --for=condition=ready --selector app=h2o-assisted pods
# Once H2O pods are ready, deploy another pod with H2O assisted clustering script into K8S
# This script has to be present inside Kubernetes in order to be able to query the H2O pods via their ClusterIP.
kubectl apply -f h2o-python-clustering.yaml
kubectl wait --timeout=180s --for=condition=ready --selector app=h2o-assisted-python pods
# Show status of H2O assisted clustering-related resources for diagnostics
echo "H2O Assisted Clustering Python script logs:"
kubectl logs -l app=h2o-assisted-python
kubectl get services
kubectl get ingresses
kubectl describe pods
# Perform the same cluster check to make sure H2O has formed a cluster of expected size
timeout 120s bash h2o-cluster-check.sh
export ASSISTED_EXIT_CODE=$?
kubectl get pods
# Make sure to delete the in-docker K3S cluster
k3d delete
# If at least one clustering phase failed, return exit code != 0 to make the stage fail
echo "Cluster checks exit code:"
echo $CLUSTER_EXIT_CODE
echo "Assisted clustering checks exit code:"
echo $ASSISTED_EXIT_CODE
export EXIT_STATUS=$(if [ "$CLUSTER_EXIT_CODE" -ne 0 ] || [ "$ASSISTED_EXIT_CODE" -ne 0 ]; then echo 1; else echo 0; fi)
exit $EXIT_STATUS
