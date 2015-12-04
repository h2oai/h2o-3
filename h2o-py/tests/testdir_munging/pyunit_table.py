from tests import pyunit_utils
import sys
sys.path.insert(1, "../../")
import h2o

def table_check():
    # Connect to a pre-existing cluster


    iris = h2o.import_frame(path=pyunit_utils.locate("smalldata/iris/iris.csv"))


    # single column (frame)
    table1 = iris["C5"].table()
    assert table1[0,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[0,0], table1[0,1])
    assert table1[1,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[1,0], table1[1,1])
    assert table1[2,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[2,0], table1[2,1])

    # two-column (one argument)
    table2 = iris["C1"].table(iris["C5"])
    print table2
    # this has changed from last version, the C1 values are sorted.
    assert table2[0,1] == 1, "Expected , but got {0}".format(table2[0,1])
    assert table2[1,1] == 3, "Expected , but got {0}".format(table2[1,1])
    assert table2[2,1] == 1, "Expected , but got {0}".format(table2[2,1])

if __name__ == "__main__":
	pyunit_utils.standalone_test(table_check)
else:
	table_check()
