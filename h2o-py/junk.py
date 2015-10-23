import os
os.chdir('c:\\Users\\cliffc\\Desktop\\h2o-3\\h2o-py')
import h2o
h2o.init()

iris = h2o.H2OFrame.read_csv("smalldata/iris/iris_wheader.csv","iris")
#iris = h2o.import_file(path="smalldata/iris/iris.csv")
# add a couple of factor columns to iris
iris = iris.cbind(iris[4] == "Iris-setosa")
iris[5] = iris[5].asfactor()
iris.set_name(5,"C6")

iris = iris.cbind(iris[4] == "Iris-virginica")
iris[6] = iris[6].asfactor()
iris.set_name(6, name="C7")

iris.show()
