
default:
	@echo
	@echo Try 'make build_cluster' to build a cluster and start H2O.
	@echo

build_cluster: b1 b2

b1:
	./h2o-cluster-launch-instances.py
	./h2o-cluster-download-h2o.sh
	./h2o-cluster-distribute-aws-credentials.sh
	./h2o-cluster-start-h2o.sh


H = $(shell cat nodes-public | head -1)

b2:
	@echo
	@echo "Cluster is available at http://$(H):54321"
	@echo

clean:
	rm -f aws_credentials.properties
	rm -f core-site.xml
	rm -f flatfile.txt
	rm -f latest
	rm -f nodes-private
	rm -f nodes-public
	rm -f project_version

