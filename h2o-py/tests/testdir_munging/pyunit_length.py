import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def length_check():
    # Connect to a pre-existing cluster

    # Test on strings
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/names.csv"), col_types=["string","string","numeric"])

    # single column (frame)
    # UTF strings
    length_frame = frame["name1"].nchar()
    assert length_frame[0,0] == 4, "Expected 4, but got {}".format(length_frame[0,0])
    assert length_frame[1,0] == 3, "Expected 3, but got {}".format(length_frame[1,0])
    assert length_frame[2,0] == 4, "Expected 4, but got {}".format(length_frame[2,0])
    # ASCII only strings
    length_frame = frame["name2"].nchar()
    assert length_frame[0,0] == 4, "Expected 4, but got {}".format(length_frame[0,0])
    assert length_frame[1,0] == 3, "Expected 3, but got {}".format(length_frame[1,0])
    assert length_frame[2,0] == 4, "Expected 4, but got {}".format(length_frame[2,0])

    # single column (vec)
    vec = frame["name1"]
    trimmed_vec = vec.trim()
    length_vec = trimmed_vec.nchar()
    assert length_vec[0,0] == 4, "Expected 4, but got {}".format(length_vec[0,0])
    assert length_vec[1,0] == 3, "Expected 3, but got {}".format(length_vec[1,0])
    assert length_vec[2,0] == 4, "Expected 4, but got {}".format(length_vec[2,0])



if __name__ == "__main__":
    pyunit_utils.standalone_test(length_check)
else:
    length_check()
