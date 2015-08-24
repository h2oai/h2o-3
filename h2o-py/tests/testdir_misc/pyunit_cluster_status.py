import sys
sys.path.insert(1, "../../")
import h2o, tests

def cluster_status_test(ip,port):
    
    

    h2o.cluster_status()

if __name__ == "__main__":
    tests.run_test(sys.argv, cluster_status_test)
