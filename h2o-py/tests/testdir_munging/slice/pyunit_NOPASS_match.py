import sys
sys.path.insert(1, "../../../")
import h2o, tests

def match_check():
    # Connect to a pre-existing cluster
    

    hex = h2o.import_file(path=h2o.locate("smalldata/iris/iris.csv"))

    print "doing the match: h2o.match(hex$Species, [\"Iris-setosa\", \"Iris-versicolor\"]"
    sub_h2o_match = hex[4].match(["Iris-setosa", "Iris-versicolor"])

    print "Printing out the subset bit vec from the match call"
    sub_h2o_match.show()

    print "performing the subsetting: hex[sub,]"
    hh_match = hex[sub_h2o_match[0] == 1]

    print "print the head of the subsetted frame"
    hh_match.show()

    print "print the dim of the subsetted frame"
    rows, cols = hh_match.dim
    print "rows: {0}".format(rows)
    print "cols: {0}".format(cols)

    print "check that the number of rows in the subsetted h2o frames match r"
    assert rows == 100 and cols == 5, "Unexpected dimensions. Got {0} rows and {1} cols.".format(rows,cols)

    # TODO: PUBDEV-1400
    print "doing the match: h2o.match(hex$Species, 5.1)"
    sub_h2o_match = hex[4].match(5.1)

    print "Printing out the subset bit vec from the match call"
    sub_h2o_match.show()

    print "performing the subsetting: hex[sub,]"
    hh_match = hex[sub_h2o_match[0] == 1]

    print "print the head of the subsetted frame"
    hh_match.show()

    print "print the dim of the subsetted frame"
    rows, cols = hh_match.dim
    print "rows: {0}".format(rows)
    print "cols: {0}".format(cols)

    print "check that the number of rows in the subsetted h2o frames match r"
    assert rows == 9 and cols == 5, "Unexpected dimensions. Got {0} rows and {1} cols.".format(rows,cols)

if __name__ == "__main__":
    tests.run_test(sys.argv, match_check)
