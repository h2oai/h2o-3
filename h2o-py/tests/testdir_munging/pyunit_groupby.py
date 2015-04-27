import sys
sys.path.insert(1, "../../")
import h2o
import pandas as pd
import numpy as np

def group_by(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    h2o_iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    pd_iris = pd.read_csv(h2o.locate("smalldata/iris/iris_wheader.csv"))

    h2o_agg_funcs = ["count","count_unique","first","last","min","max","mean","avg","sd","stdev","var","sum","ss"]
    na_handling = ["ignore","rm","all"]
    col_names = h2o_iris.col_names()[0:4]

    # smoke test
    for a in h2o_agg_funcs:
        for n in na_handling:
            for c in col_names:
                h2o.group_by(h2o_iris, ["class"], {"foo":[a,c,n]})

    # h2o/pandas/numpy comparison test
    h2o_np_agg_dict = {"min":np.min, "max":np.max, "mean":np.mean, "sum":np.sum}
    for k in h2o_np_agg_dict.keys():
        for c in col_names:
            h2o_res = h2o.group_by(h2o_iris, ["class"], {"foo":[k,c,"all"]})
            pd_res = pd_iris.groupby("class")[c].aggregate(h2o_np_agg_dict[k])
            for i in range(3):
                h2o_val = h2o_res[i,1]
                pd_val = pd_res.values[int(h2o_res[i,0])]
                assert abs(h2o_val - pd_val) < 1e-06, \
                    "check unsuccessful! h2o computed {0} and pandas computed {1}. expected equal aggregate {2} " \
                    "values between h2o and pandas on column {3}".format(h2o_val,pd_val,k,c)

if __name__ == "__main__":
    h2o.run_test(sys.argv, group_by)