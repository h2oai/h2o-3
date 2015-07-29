import sys
sys.path.insert(1, "../../")
import h2o

def pyunit_which(ip,port):

    iris = h2o.import_frame(h2o.locate("smalldata/iris/iris.csv"))
    setosa = h2o.which(iris[4]=="Iris-setosa")
    versicolor = h2o.which(iris[4]=="Iris-versicolor")
    virginica = h2o.which(iris[4]=="Iris-virginica")

    assert sum(range(0,50)) == setosa.sum()
    assert sum(range(50,100)) == versicolor.sum()
    assert sum(range(100,150)) == virginica.sum()

if __name__ == "__main__":
    h2o.run_test(sys.argv, pyunit_which)