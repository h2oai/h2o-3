import sys
sys.path.insert(1, "../../")
import h2o

def ls_test(ip,port):
    # Connect to h2o
    

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris.csv"))

    h2o.ls()

if __name__ == "__main__":
    h2o.run_test(sys.argv, ls_test)
