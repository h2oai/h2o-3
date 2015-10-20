import h2o
h2o.init()
x = h2o.H2OFrame.read_csv("smalldata/iris/iris.csv","iris")
print(x.nrow)
print(x.ncol)
h2o.remove(x)
