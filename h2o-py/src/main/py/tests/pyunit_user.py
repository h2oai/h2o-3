import h2o
import tabulate

# #####################################################
#
# Sample use-cases

# Connect to a pre-existing cluster
h2o.init(ip="localhost", port=54321)

a = h2o.import_frame(path="smalldata/iris/iris_wheader.csv")[0:4]
a.head()

print a[0].name()  # Column header
a[0][2].show()  # column 0, row 2 value
a["sepal_len"][2].show()  # Column 0, row 2 value
(a[0] + 2).show()  # Add 2 to every element; broadcast a constant
(a[0] + a[1]).show()  # Add 2 columns; broadcast parallel add
sum(a).show()
a["sepal_len"].mean().show()

print
print "Rows 50 through 77 in the `sepal_len` column"
a["sepal_len"][50:78].show()  # print out rows 50 thru 77 inclusive
print

a["sepal_len"].show()

# print a[["sepal_len", "sepal_wid"]][50:78].show()

a.show()

colmeans = [v.mean().eager() for v in a]

print "The column means: "
print colmeans
print

try:
    print a["Sepal_len"]  # Error, mispelt column name
except ValueError, ex:
    pass  # Expected error

b = h2o.import_frame(path="smalldata/iris/iris_wheader.csv")[0:4]
c = a + b
d = c + c + sum(a)
e = c + a + 1
e.show()
# Note that "d=c+..." keeps the internal C expressions alive, until "d" goes
# out of scope even as we nuke "c"
c.show()
c = None
# Internal "Expr(c=a+b)" not dead!

print 1 + (a[0] + b[1]).mean()

import collections

c = h2o.H2OFrame(python_obj=collections.OrderedDict({"A": [1, 2, 3], "B": [4, 5, 6]}))
c.show()

c.describe()
c.head()

c[0].show()
c[0][1].show()
c[0][0:2].show()

sliced = a[0][0:51]
sliced.show()
