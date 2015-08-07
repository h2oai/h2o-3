import sys
sys.path.insert(1, "../../")
import h2o

def space_headers(ip,port):
    # Connect to h2o
    

    f = h2o.import_frame(path=h2o.locate("smalldata/jira/citibike_head.csv"))

    print f.names()

    f["starttime"].show()

    h2o_median = f["start station id"].median()

    assert h2o_median == 444, "Expected median for \"start station id\" to be 444, but got {0}".format(h2o_median)

if __name__ == "__main__":
    h2o.run_test(sys.argv, space_headers)
