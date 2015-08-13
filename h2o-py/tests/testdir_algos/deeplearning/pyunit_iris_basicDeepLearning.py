import sys, os
sys.path.insert(1,"../../../")
import h2o

def deeplearning_basic(ip, port):
    

    iris_hex = h2o.import_file(path=h2o.locate("smalldata/iris/iris.csv"))
    hh = h2o.deeplearning(x=iris_hex[:3],
                          y=iris_hex[4],
                          loss='CrossEntropy')
    hh.show()

if __name__ == '__main__':
    h2o.run_test(sys.argv, deeplearning_basic)
