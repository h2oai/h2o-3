import sys
sys.path.insert(1, "../../")
import h2o

def toupper_tolower_check(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    frame = h2o.import_frame(path=h2o.locate("smalldata/iris/iris.csv"))

    # single column (frame)
    frame["C5"] = frame["C5"].toupper()
    assert frame[0,4] == "IRIS-SETOSA", "Expected 'IRIS-SETOSA', but got {0}".format(frame[0,4])

    # single column (vec)
    vec = frame["C5"]
    vec = vec.toupper()
    assert vec[2,0] == "IRIS-SETOSA", "Expected 'IRIS-SETOSA', but got {0}".format(vec[2,0])

    vec = vec.tolower()
    assert vec[3,0] == "iris-setosa", "Expected 'iris-setosa', but got {0}".format(vec[3,0])

if __name__ == "__main__":
    h2o.run_test(sys.argv, toupper_tolower_check)