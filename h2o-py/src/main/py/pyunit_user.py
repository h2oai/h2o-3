import h2o

# #####################################################
#
# Sample use-cases

# Connect to a pre-existing cluster
h2o.init(ip="localhost", port=54321)  # handle to H2O stashed away

a = h2o.H2OFrame(remote_fname="smalldata/iris/iris_wheader.csv")[0:4]

print a[0].name()  # Column header
print a[0][2].show()  # column 0, row 2 value
print a["sepal_len"][2].show()  # Column 0, row 2 value
print (a[0] + 2).show()  # Add 2 to every element; broadcast a constant
print (a[0] + a[1]).show()  # Add 2 columns; broadcast parallel add
print sum(a).show()
print a["sepal_len"].mean().show()

try:
    print a["Sepal_len"]  # Error, mispelt column name
except ValueError, ex:
    pass  # Expected error

b = h2o.H2OFrame(remote_fname="smalldata/iris/iris_wheader.csv")[0:4]
c = a + b
d = c + c + sum(a)
e = c + a + 1
print e.show()
# Note that "d=c+..." keeps the internal C expressions alive, until "d" goes
# out of scope even as we nuke "c"
print c.show()
c = None
# Internal "Expr(c=a+b)" not dead!

print 1 + (a[0] + b[1]).mean()


# c = H2OFrame(vecs=[H2OVec("C1", h2o.Expr([1, 2, 3])), H2OVec("C2", h2o.Expr([4, 5, 6]))])
# print c.show()

from h2o import H2O_GBM


my_gbm = H2O_GBM()
my_gbm.x = [0, 1, 2, 3]
my_gbm.y = 4
my_gbm.training_frame = a
my_gbm.ntrees = 50
my_gbm.max_depth = 5

my_gbm.fit()

############################

from h2o.model.h2o_gbm_builder import GBMBuilder

my_gbm2 = GBMBuilder()

my_gbm2.x = [0, 1, 2, 3]
my_gbm2.y = 4
my_gbm2.training_frame = a
my_gbm2.validation_frame = a
my_gbm2.ntrees = 50
my_gbm2.max_depth = 5

my_gbm2.fit()
