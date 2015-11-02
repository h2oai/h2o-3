import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def vec_show():
    
    

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    print "iris:"
    iris.show()

    ###################################################################

    res = 2 - iris
    res2 = res[0]
    print "res2:"
    res2.show()

    res3 = res[1]
    print "res3:"
    res3.show()

    iris[2].show()



if __name__ == "__main__":
    pyunit_utils.standalone_test(vec_show)
else:
    vec_show()
