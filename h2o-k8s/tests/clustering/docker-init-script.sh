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
k3d create -v $H2O_BASE/build/h2o.jar:$H2O_BASE/build/h2o.jar --registries-file registries.yaml --publish 8080:80 --api-port localhost:6444 --server-arg --tls-san="127.0.0.1" --wait 120 
export KUBECONFIG="$(k3d get-kubeconfig --name='k3s-default')"
kubectl cluster-info
sleep 15 # Making sure the default namespace is initialized. The --wait flag does not guarantee this.
kubectl get namespaces
envsubst < h2o-service.yaml >> h2o-service-subst.yaml
kubectl apply -f h2o-service-subst.yaml
kubectl wait --for=condition=available --timeout=600s deployment.apps/h2o-deployment -n default
rm h2o-service-subst.yaml
kubectl describe pods
timeout 120s bash h2o-cluster-check.sh
export EXIT_STATUS=$?
kubectl get pods
kubectl get nodes
k3d delete
exit $EXIT_STATUS
