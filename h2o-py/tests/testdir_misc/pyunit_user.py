import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils





def user():

    a = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))[0:4]
    a.head()

    print a[0].names  # Column header
    print a[2,0]           # column 0, row 2 value
    print a[2,"sepal_len"] # Column 0, row 2 value
    (a[0] + 2).show()  # Add 2 to every element; broadcast a constant
    (a[0] + a[1]).show()  # Add 2 columns; broadcast parallel add
    sum(a).show()
    print a["sepal_len"].mean()[0]

    print
    print "Rows 50 through 77 in the `sepal_len` column"
    a[50:78, "sepal_len"].show()  # print out rows 50 thru 77 inclusive
    print

    a["sepal_len"].show()

    print a[50:78, ["sepal_len", "sepal_wid"]].show()

    a.show()

    colmeans = a.mean()

    print "The column means: "
    print colmeans
    print

    try:                   print a["Sepal_len"].dim  # Error, mispelt column name
    except Exception: pass  # Expected error

    b = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))[0:4]
    c = a + b
    d = c + c + sum(a)
    e = c + a + 1
    e.show()
    # Note that "d=c+..." keeps the internal C expressions alive, until "d" goes
    # out of scope even as we nuke "c"
    c.show()
    c = None
    # Internal "ExprNode(c=a+b)" not dead!

    print 1 + (a[0] + b[1]).mean()[0]

    import collections

    c = h2o.H2OFrame(collections.OrderedDict({"A": [1, 2, 3], "B": [4, 5, 6]}))
    c.show()

    c.describe()
    c.head()

    c[0].show()
    print c[1,0]
    c[0:2,0].show()

    sliced = a[0:51,0]
    sliced.show()



if __name__ == "__main__":
    pyunit_utils.standalone_test(user)
else:
    user()
