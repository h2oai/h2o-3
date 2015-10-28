import os
os.chdir('c:\\Users\\cliffc\\Desktop\\h2o-3\\h2o-py')
import h2o
h2o.init()

python_obj = [["a", "b", "cc"], ["c", "d", "ee"]]
x = h2o.H2OFrame.fromPython(python_obj)

#iris = h2o.H2OFrame.read_csv("smalldata/iris/iris_wheader.csv","iris")
