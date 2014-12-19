from h2o import H2OConnection
from h2o import H2OFrame

######################################################
#
# Sample use-cases

# Connect to a pre-existing cluster
cluster = H2OConnection()

a = H2OFrame(remoteFName="smalldata/iris/iris_wheader.csv")[0:4]

print a[0]._name    # Column header
print a[0][2]       # column 0, row 2 value
print a["sepal_len"][2]  # Column 0, row 2 value
print a[0]+2        # Add 2 to every element; broadcast a constant
print a[0]+a[1]     # Add 2 columns; broadcast parallel add
print sum(a)
print a["sepal_len"].mean()

try: print a["Sepal_len"]   # Error, mispelt column name
except ValueError,ex: pass  # Expected error

b = H2OFrame(remoteFName="smalldata/iris/iris_wheader.csv")[0:4]
c = a+b
d = c+c+sum(a)
e = c+a+1
print e
# Note that "d=c+..." keeps the internal C expressions alive, until "d" goes
# out of scope even as we nuke "c"
print c
c = None
# Internal "Expr(c=a+b)" not dead!

print 1+(a[0]+b[1]).mean()

from h2o import Vec, Expr
c = H2OFrame(vecs=[Vec("C1",Expr([1,2,3])),Vec("C2",Expr([4,5,6]))])
print c
