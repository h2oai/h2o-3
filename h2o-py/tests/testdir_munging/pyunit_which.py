import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def pyunit_which():

    iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
    setosa = h2o.which(iris[4]=="Iris-setosa")
    versicolor = h2o.which(iris[4]=="Iris-versicolor")
    virginica = h2o.which(iris[4]=="Iris-virginica")

    assert sum(range(0,50)) == setosa.sum()
    assert sum(range(50,100)) == versicolor.sum()
    assert sum(range(100,150)) == virginica.sum()



if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_which)
else:
    pyunit_which()
