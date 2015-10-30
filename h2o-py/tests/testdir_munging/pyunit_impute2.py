import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def impute2():
    # Connect to a pre-existing cluster
    

    prostate = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate_missing.csv"))
    methods = ["mean","median","mode"]
    combine_methods=["interpolate", "average", "low", "high"]
    inplace = [False, True]

    for inpl in inplace:
        for method in methods:
            for combine_method in combine_methods:
              prostate.impute("DPROS", method=method, combine_method=combine_method, inplace=inpl)

#    air = h2o.upload_file(pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"))
#    for inpl in inplace:
#        for method in methods:
#            for combine_method in combine_methods:
#              air.impute( "TailNum", method = method, combine_method = combine_method, inplace = inpl)

    data = [[None, 2,    3,    1,    'a',  1,    9],
            [1,    None, 4,    2,    'a',  1,    9],
            [2,    3,    None, None, 'b',  1,    9],
            [3,    4,    None, None, 'b',  3,    8],
            [4,    5,    9,    5,    None, 2,    8],
            [5,    None, 10,   7,    'b',  None, 8]]
    h2o_data = h2o.H2OFrame(zip(*data))

    # mean check
    h2o_data.impute(column="C1", method="mean")
    c1_imputed = h2o_data[0,0]
    assert c1_imputed == 3, "Wrong value imputed. Expected imputed value of 3, but got {0}".format(c1_imputed)

    # inplace check
    h2o_data = h2o.H2OFrame(zip(*data))
    h2o_data.impute(column="C1", method="mean", inplace=False)
    assert h2o_data["C1"].isna().sum() == 1, "Expected imputation to be done in place."

    # median-average
    h2o_data = h2o.H2OFrame(zip(*data))
    h2o_data.impute( column="C2", method="median", combine_method="average")
    c2_imputed = h2o_data[1,1]
    assert c2_imputed == 3.5, "Wrong value imputed. Expected imputed value of 3.5, but got {0}".format(c2_imputed)

    # median-low
    h2o_data = h2o.H2OFrame(zip(*data))
    h2o_data.impute(column="C3", method="median", combine_method="low")
    c3_imputed = h2o_data[2,2]
    assert c3_imputed == 4, "Wrong value imputed. Expected imputed value of 4, but got {0}".format(c3_imputed)

    # median-high
    h2o_data = h2o.H2OFrame(zip(*data))
    h2o_data.impute(column="C4", method="median", combine_method="high")
    c4_imputed = h2o_data[2,3]
    assert c4_imputed == 5, "Wrong value imputed. Expected imputed value of 5, but got {0}".format(c4_imputed)

    # mode-categorical
    h2o_data = h2o.H2OFrame(zip(*data))
    h2o_data.impute(column="C5", method="mode")
    c5_imputed = h2o_data[4,4]
    assert c5_imputed == 'b', "Wrong value imputed. Expected imputed value of b, but got {0}".format(c5_imputed)

    # mode-numeric
    h2o_data = h2o.H2OFrame(zip(*data))
    h2o_data.impute(column="C6", method="mode")
    c6_imputed = h2o_data[5,5]
    assert c6_imputed == 1, "Wrong value imputed. Expected imputed value of 1, but got {0}".format(c6_imputed)

    # mean-group by C7
    h2o_data = h2o.H2OFrame(zip(*data))
    h2o_data.impute(column="C3", method="mean", by="C7")
    imputed1 = h2o_data[2,2]
    imputed2 = h2o_data[3,2]
    assert imputed1 == 3.5, "Wrong value imputed. Expected imputed value of 3.5, but got {0}".format(imputed1)
    assert imputed2 == 9.5, "Wrong value imputed. Expected imputed value of 9.5, but got {0}".format(imputed2)



if __name__ == "__main__":
    pyunit_utils.standalone_test(impute2)
else:
    impute2()
