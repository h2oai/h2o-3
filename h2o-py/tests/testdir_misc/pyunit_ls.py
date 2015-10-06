import sys
sys.path.insert(1, "../../")
import h2o, tests

def ls_test():
    
    

    iris = h2o.import_file(path=tests.locate("smalldata/iris/iris.csv"))

    h2o.ls()

if __name__ == "__main__":
    tests.run_test(sys.argv, ls_test)
