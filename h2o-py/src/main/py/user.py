import h2o

######################################################
#
# Sample use-cases

# Connect to a pre-existing cluster
cluster = h2o.Cluster()

a = h2o.Frame(cluster,fname="smalldata/iris/iris_wheader.csv")[0:4]

print a[0]._name    # Column header
print a[0][2]       # column 0, row 2 value
print a["a.sepal_len"][2]  # Column 0, row 2 value
print a[0]+2        # Add 2 to every element; broadcast a constant
print a[0]+a[1]     # Add 2 columns; broadcast parallel add
print a["a.sepal_len"].mean()
print sum(a)

try: print a["a.Sepal_len"]  # Error, mispelt column name
except ValueError,ex: pass  # Expected error

b = h2o.Frame("b.",fname="smalldata/iris/iris_wheader.csv")[0:4]
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

c = h2o.Frame(vecs=[h2o.Vec("C1",h2o.Expr([1,2,3])),h2o.Vec("C2",h2o.Expr([4,5,6]))])
print c
