import h2o
h2o.init()
x = h2o.H2OFrame.read_csv("smalldata/iris/iris_wheader.csv","iris")
print(x.nrow)
print(x.ncol)
print(x.names)
print(x.name(2))
print(x.__repr__)
h2o.remove(x)
