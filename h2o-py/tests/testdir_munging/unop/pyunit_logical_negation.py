import sys
sys.path.insert(1, "../../../")
import h2o
import numpy as np

def logical_negation(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    h2o_data = h2o.import_frame(path=h2o.locate("smalldata/logreg/prostate.csv"))
    local_data = h2o.as_list(h2o_data)
    np_data = np.loadtxt(h2o.locate("smalldata/logreg/prostate.csv"), delimiter=',', skiprows=1)

    # frame type input
    new_h2o_data = h2o.logical_negation(h2o_data)
    new_np_data = np.logical_not(np_data)
    ## checks
    h2o.dim_check(h2o_data, new_h2o_data)
    h2o.np_comparison_check(new_h2o_data, new_np_data, 10)
    h2o.value_check(h2o_data, local_data, 10)

    # vec type input
    new_h2o_data = h2o.logical_negation(h2o_data["CAPSULE"])
    new_np_data = np.logical_not(np_data[:,1])
    ## checks
    h2o.dim_check(h2o_data["CAPSULE"], new_h2o_data)
    h2o.np_comparison_check(new_h2o_data, new_np_data, 10)
    h2o.value_check(h2o_data, local_data, 10, col=1)

    # expr type input
    new_h2o_data = h2o.logical_negation(h2o_data * 2)
    new_np_data = np.logical_not(np_data * 2)
    ## checks
    h2o.dim_check(h2o_data, new_h2o_data)
    h2o.np_comparison_check(new_h2o_data, new_np_data, 10)
    h2o.value_check(h2o_data, local_data, 10)

if __name__ == "__main__":
    h2o.run_test(sys.argv, logical_negation)
