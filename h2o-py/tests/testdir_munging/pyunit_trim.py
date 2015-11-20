import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def trim_check():
    # Connect to a pre-existing cluster
    # testing on a string column
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_trim.csv"), col_types=["string","numeric","numeric","numeric","numeric","numeric","numeric","numeric"])

    # single column (frame)
    trimmed_frame = frame["name"].trim()
    assert trimmed_frame[0,0] == "AMC Ambassador Brougham", "Expected 'AMC Ambassador Brougham', but got {}".format(trimmed_frame[0,0])
    assert trimmed_frame[1,0] == "AMC Ambassador DPL", "Expected 'AMC Ambassador DPL', but got {}".format(trimmed_frame[1,0])
    assert trimmed_frame[2,0] == "AMC Ambassador SST", "Expected 'AMC Ambassador SST', but got {}".format(trimmed_frame[2,0])

    # single column (vec)
    vec = frame["name"]
    trimmed_vec = vec.trim()
    assert trimmed_vec[0,0] == "AMC Ambassador Brougham", "Expected 'AMC Ambassador Brougham', but got {}".format(trimmed_frame[0,0])
    assert trimmed_vec[1,0] == "AMC Ambassador DPL",      "Expected 'AMC Ambassador DPL', but got {}".format(trimmed_frame[1,0])
    assert trimmed_vec[2,0] == "AMC Ambassador SST",      "Expected 'AMC Ambassador SST', but got {}".format(trimmed_frame[2,0])



if __name__ == "__main__":
    pyunit_utils.standalone_test(trim_check)
else:
    trim_check()
