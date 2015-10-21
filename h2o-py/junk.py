import os
os.chdir('c:\\Users\\cliffc\\Desktop\\h2o-3\\h2o-py')
import h2o
h2o.init()
x = h2o.H2OFrame.read_csv("smalldata/iris/iris_wheader.csv","iris")
print(x.repr())
print(x)
h2o.remove(x)
