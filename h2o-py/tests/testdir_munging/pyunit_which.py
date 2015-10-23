import sys
sys.path.insert(1, "../../")
import h2o, tests

def pyunit_which():

    iris = h2o.import_file(tests.locate("smalldata/iris/iris.csv"))
    setosa = (iris[4]=="Iris-setosa").which()
    versicolor = (iris[4]=="Iris-versicolor").which()
    virginica = (iris[4]=="Iris-virginica").which()

    assert sum(range(0,50)) == setosa.sum()
    assert sum(range(50,100)) == versicolor.sum()
    assert sum(range(100,150)) == virginica.sum()

if __name__ == "__main__":
    tests.run_test(sys.argv, pyunit_which)
