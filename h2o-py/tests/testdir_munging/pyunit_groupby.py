from tests import pyunit_utils
import sys
sys.path.insert(1, "../../")
import h2o
import pandas as pd
import numpy as np

def group_by():
    # Connect to a pre-existing cluster
    

    h2o_iris = h2o.import_frame(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    pd_iris = pd.read_csv(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))


    h2o_agg_funcs = ["count","count_unique","first","last","min","max","mean","avg","sd","stdev","var","sum","ss"]
    na_handling = ["ignore","rm","all"]
    col_names = h2o_iris.col_names[0:4]

    print "Running smoke test"

    # smoke test
    for a in h2o_agg_funcs:
        for n in na_handling:
            for c in col_names:
                print "group by : " + str(a) + "; " + str(n) + "; " + str(c)
                h2o_iris.group_by("class")

if __name__ == "__main__":
	pyunit_utils.standalone_test(group_by)
else:
	group_by()
