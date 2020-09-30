#! /bin/bash -x

pwd
export H2O_BASE=$(pwd)
if [[ $string == *"@"* ]]; then
  echo "H2O base path contains at sign. Unable to create K3S cluster."
  exit 1
fi
cd $H2O_BASE/h2o-k8s/tests/clustering/
k3d --version
k3d cluster delete
k3d cluster create -v $H2O_BASE/build/h2o.jar:$H2O_BASE/build/h2o.jar -v registries.yaml:/etc/rancher/k3s/registries.yaml -p 8080:80@loadbalancer --wait
export KUBECONFIG="$(k3d kubeconfig write k3s-default)"
kubectl cluster-info
sleep 15 # Making sure the default namespace is initialized. The --wait flag does not guarantee this.
kubectl get namespaces
envsubst < testvalues-template.yaml >> testvalues.yaml
helm install -f testvalues.yaml h2o $H2O_BASE/h2o-helm/
helm test h2o
kubectl describe pods
timeout 120s bash h2o-cluster-check.sh
export EXIT_STATUS=$?
kubectl get pods
kubectl get nodes
k3d cluster delete
exit $EXIT_STATUS
