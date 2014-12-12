import h2o

######################################################
#
# Sample use-cases

a = h2o.Frame("a.",fname="smalldata/iris/iris_wheader.csv")[0:4]

print a[0]._name    # Column header
print a[0][2]       # column 0, row 2 value
print a["a.sepal_len"][2]  # Column 0, row 2 value
print a[0]+2        # Add 2 to every element; broadcast a constant
print a[0]+a[1]     # Add 2 columns; broadcast parallel add
print sum(a["a.sepal_len"])/len(a[0])
print sum(a)

try: print a["a.Sepal_len"]  # Error, mispelt column name
except ValueError,ex: pass  # Expected error

b = h2o.Frame("b.",fname="smalldata/iris/iris_wheader.csv")[0:4]
c = a+b
d = c+c+sum(a)
e = c+a+1
print e
print c

print 1+(a[0]+b[1]).mean()
