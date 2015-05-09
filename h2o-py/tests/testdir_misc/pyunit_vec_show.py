import sys
sys.path.insert(1, "../../")
import h2o
from h2o.frame import H2OVec

def vec_show(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    print "iris:"
    iris.show()

    ###################################################################

    res = 2 - iris
    res2 = H2OVec(name="C0", expr=res[0])
    print "res2:"
    res2.show()

    res3 = H2OVec(name="C1", expr=res[1])
    print "res3:"
    res3.show()

    iris[2].show()

if __name__ == "__main__":
    h2o.run_test(sys.argv, vec_show)
