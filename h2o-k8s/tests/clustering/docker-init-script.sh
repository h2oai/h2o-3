#! /bin/bash -x

pwd
export H2O_BASE=$(pwd)
if [[ $string == *"@"* ]]; then
  echo "H2O base path contains at sign. Unable to create K3S cluster."
  exit 1
fi

# Force in-house version of the image in tests
sed -i.bu 's,everpeace,harbor.h2o.ai/opsh2oai,' $H2O_BASE/h2o-helm/templates/tests/test-connection.yaml
rm $H2O_BASE/h2o-helm/templates/tests/test-connection.yaml.bu
cat $H2O_BASE/h2o-helm/templates/tests/test-connection.yaml

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
envsubst < testvalues-template.yaml > testvalues.yaml
helm install -f testvalues.yaml h2o $H2O_BASE/h2o-helm --kubeconfig $KUBECONFIG --dry-run # Shows resulting YAML
helm install -f testvalues.yaml h2o $H2O_BASE/h2o-helm --kubeconfig $KUBECONFIG
# Use helm built-in test pod
helm test h2o
# After the deployment, show status of H2O-related K8S resources
kubectl logs h2o-h2o-3-test-connection
CLOUDING_RESULT=$(kubectl logs h2o-h2o-3-test-connection | grep -F 'CLOUDING-RESULT' | tail -1)
kubectl get ingresses
kubectl describe pods
kubectl get pods
kubectl get nodes
# To save resources in Jenkins pipeline (e.g. CPUs are limited), remove the H2O cluster deployed via HELM
helm uninstall h2o 
# Make sure to delete the in-docker K3S cluster
k3d delete
# If at least one clustering phase failed, return exit code != 0 to make the stage fail
if [ "$CLOUDING_RESULT" != "CLOUDING-RESULT: OK" ]; then
  echo "$CLOUDING_RESULT"
  exit 1
fi
