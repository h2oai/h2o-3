import sys
sys.path.insert(1, "../../")
import h2o, tests

def length_check():
    # Connect to a pre-existing cluster

    # Test on strings
    frame = h2o.import_file(path=tests.locate("smalldata/junit/names.csv"), col_types=["string","string","numeric"])

    # single column (frame)
    # UTF strings
    length_frame = frame["name1"].length()
    assert length_frame[0,0] == 4, "Expected 4, but got {}".format(length_frame[0,0])
    assert length_frame[1,0] == 3, "Expected 3, but got {}".format(length_frame[1,0])
    assert length_frame[2,0] == 4, "Expected 4, but got {}".format(length_frame[2,0])
    # ASCII only strings
    length_frame = frame["name2"].length()
    assert length_frame[0,0] == 4, "Expected 4, but got {}".format(length_frame[0,0])
    assert length_frame[1,0] == 3, "Expected 3, but got {}".format(length_frame[1,0])
    assert length_frame[2,0] == 4, "Expected 4, but got {}".format(length_frame[2,0])

    # single column (vec)
    vec = frame["name1"]
    trimmed_vec = vec.trim()
    length_vec = trimmed_vec.length()
    assert length_vec[0,0] == 4, "Expected 4, but got {}".format(length_vec[0,0])
    assert length_vec[1,0] == 3, "Expected 3, but got {}".format(length_vec[1,0])
    assert length_vec[2,0] == 4, "Expected 4, but got {}".format(length_vec[2,0])

if __name__ == "__main__":
    tests.run_test(sys.argv, length_check)
