import h2o

# #####################################################
#
# Sample use-cases

# Connect to a pre-existing cluster
h2o.connect(ip="localhost", port=54321)

a = h2o.import_frame(path="smalldata/iris/iris_wheader.csv")[0:4]

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

b = h2o.import_frame(path="smalldata/iris/iris_wheader.csv")[0:4]
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

c = h2o.import_frame(vecs=[h2o.H2OVec("C1", h2o.Expr([1, 2, 3])),
                           h2o.H2OVec("C2", h2o.Expr([4, 5, 6])),
                           ])
print c.show()
