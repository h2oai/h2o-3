import os
os.chdir('c:\\Users\\cliffc\\Desktop\\h2o-3\\h2o-py')
import h2o
h2o.init()
x = h2o.H2OFrame.read_csv("smalldata/iris/iris_wheader.csv","iris")
print(x)
x.summary()
print(x[1])
print(x['sepal_len']+1)

x['C6'] = x['sepal_len'] > 0.0
xany = x[:,"C6"].any()
xall = x[:,"C6"].all()
print(xany,xall)

x['C6'] = x['sepal_len'] > 5.0
xany = x[:,"C6"].any()
xall = x[:,"C6"].all()
print(xany,xall)
scalar = x[1,2]
print(type(scalar))
print(scalar)

x[1,2]==1.4
x.__getitem__((1,2))
