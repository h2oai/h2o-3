import sys
sys.path.insert(1, "../../")
import h2o, tests

def table_check():
    # Connect to a pre-existing cluster
    

    iris = h2o.import_file(path=h2o.locate("smalldata/iris/iris.csv"))

    # single column (frame)
    table1 = iris["C5"].table()
    assert table1[0,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[0,0], table1[0,1])
    assert table1[1,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[1,0], table1[1,1])
    assert table1[2,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[2,0], table1[2,1])

    # two-column (one argument)
    table2 = iris["C1"].table(iris["C5"])
    assert table2[0,2] == 4, "Expected , but got {0}".format(table2[0,2])
    assert table2[1,2] == 5, "Expected , but got {0}".format(table2[1,2])
    assert table2[2,2] == 3, "Expected , but got {0}".format(table2[2,2])

if __name__ == "__main__":
    tests.run_test(sys.argv, table_check)
