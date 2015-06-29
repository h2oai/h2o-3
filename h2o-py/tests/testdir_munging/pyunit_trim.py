import sys
sys.path.insert(1, "../../")
import h2o

def trim_check(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    frame = h2o.import_frame(path=h2o.locate("smalldata/junit/cars_trim.csv"))

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
    h2o.run_test(sys.argv, trim_check)