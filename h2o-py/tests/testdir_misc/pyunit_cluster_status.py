import sys
sys.path.insert(1, "../../")
import h2o

def cluster_status_test(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    h2o.cluster_status()

if __name__ == "__main__":
    h2o.run_test(sys.argv, cluster_status_test)
