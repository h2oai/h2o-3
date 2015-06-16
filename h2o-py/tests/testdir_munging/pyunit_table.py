import sys
sys.path.insert(1, "../../")
import h2o

def table_check(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris.csv"))

    # single column (frame)
    table1 = h2o.table(iris[["C5"]])
    assert table1[0,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[0,0], table1[0,1])
    assert table1[1,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[1,0], table1[1,1])
    assert table1[2,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[2,0], table1[2,1])

    # single column (vec)
    table1 = h2o.table(iris["C5"])
    assert table1[0,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[0,0], table1[0,1])
    assert table1[1,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[1,0], table1[1,1])
    assert table1[2,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[2,0], table1[2,1])

    # two-column (one argument)
    table2 = h2o.table(iris[["C1", "C5"]])
    assert table2[0,2] == 4, "Expected , but got {0}".format(table2[0,2])
    assert table2[1,2] == 5, "Expected , but got {0}".format(table2[1,2])
    assert table2[2,2] == 3, "Expected , but got {0}".format(table2[2,2])

    # two columns (seperate arguments (frames))
    table3 = h2o.table(iris[["C1"]],iris[["C5"]])
    assert table3[0,2] == 4, "Expected , but got {0}".format(table3[0,2])
    assert table3[1,2] == 5, "Expected , but got {0}".format(table3[1,2])
    assert table3[2,2] == 3, "Expected , but got {0}".format(table3[2,2])

    # two columns (seperate arguments (vecs))
    table3 = h2o.table(iris["C1"],iris["C5"])
    assert table3[0,2] == 4, "Expected , but got {0}".format(table3[0,2])
    assert table3[1,2] == 5, "Expected , but got {0}".format(table3[1,2])
    assert table3[2,2] == 3, "Expected , but got {0}".format(table3[2,2])

if __name__ == "__main__":
    h2o.run_test(sys.argv, table_check)