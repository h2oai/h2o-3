import sys
sys.path.insert(1, "../../")
import h2o, tests

def length_check():
    # Connect to a pre-existing cluster

    # Test on strings
    frame = h2o.import_file(path=h2o.locate("smalldata/junit/cars_trim.csv"), col_types=["string","numeric","numeric","numeric","numeric","numeric","numeric","numeric"])

    # single column (frame)
    length_frame = frame["name"].length()
    assert length_frame[0,0] == 26, "Expected 26, but got {}".format(length_frame[0,0])
    assert length_frame[1,0] == 19, "Expected 19, but got {}".format(length_frame[1,0])
    assert length_frame[2,0] == 19, "Expected 19, but got {}".format(length_frame[2,0])

    # single column (vec)
    vec = frame["name"]
    trimmed_vec = vec.trim()
    length_vec = trimmed_vec.length()
    assert length_vec[0,0] == 23, "Expected 23, but got {}".format(length_vec[0,0])
    assert length_vec[1,0] == 18, "Expected 18, but got {}".format(length_vec[1,0])
    assert length_vec[2,0] == 18, "Expected 18, but got {}".format(length_vec[2,0])

if __name__ == "__main__":
    tests.run_test(sys.argv, length_check)
