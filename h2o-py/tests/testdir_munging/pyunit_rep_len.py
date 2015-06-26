import sys
sys.path.insert(1, "../../")
import h2o

def rep_len_check(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris.csv"))

    # data is int
    fr = h2o.rep_len(42, length_out=5)
    assert fr.nrow() == 5 and fr.ncol() == 1, "Expected an H2OFrame with 5 rows and 1 column, but got {0} rows and {1} " \
                                              "cols".format(fr.nrow(), fr.ncol())
    for r in range(fr.nrow()): assert fr[r,0] == 42, "Expected 42, but got {0}".format(fr[r,0])

    # data is string
    fr = h2o.rep_len("foo", length_out=22)
    assert fr.nrow() == 22 and fr.ncol() == 1, "Expected an H2OFrame with 22 rows and 1 column, but got {0} rows and {1} " \
                                              "cols".format(fr.nrow(), fr.ncol())
    for r in range(fr.nrow()): assert fr[r,0] == "foo", "Expected \"foo\", but got {0}".format(fr[r,0])

    # data is single column (vec)
    vec = h2o.rep_len(iris[0], length_out=301)
    assert len(vec) == 301, "Expected an H2OVec with 301 rows, but got {0} rows".format(len(vec))
    for r in range(len(vec)): assert vec[r] == vec[r % 150], "Expected {0}, but got {1}".format(vec[r % 150], vec[r])

    # data is frame
    #TODO: there's a NPE bug here
    #fr = h2o.rep_len(iris, length_out=7)
    #assert fr.nrow() == 150 and fr.ncol() == 7, "Expected an H2OFrame with 150 rows and 7 columns, but got {0} rows and {1} " \
    #                                            "cols".format(fr.nrow(), fr.ncol())

if __name__ == "__main__":
    h2o.run_test(sys.argv, rep_len_check)