import sys
sys.path.insert(1, "../../")
import h2o, tests

def toupper_tolower_check():
    # Connect to a pre-existing cluster
    

    frame = h2o.import_file(path=h2o.locate("smalldata/iris/iris.csv"), col_types=["numeric","numeric","numeric","numeric","string"])

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
    tests.run_test(sys.argv, toupper_tolower_check)
