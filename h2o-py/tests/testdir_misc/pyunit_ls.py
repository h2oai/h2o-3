from tests import pyunit_utils
import sys
sys.path.insert(1, "../../")
import h2o

def ls_test():
    
    

    iris = h2o.import_frame(path=pyunit_utils.locate("smalldata/iris/iris.csv"))


    h2o.ls()

if __name__ == "__main__":
	pyunit_utils.standalone_test(ls_test)
else:
	ls_test()
