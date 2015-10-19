

import h2o, tests

def pyunit_assign():

    pros = h2o.import_file(tests.locate("smalldata/prostate/prostate.csv"))
    pq = pros.quantile()

    PSA_outliers = pros[pros["PSA"] <= pq[1,1] or pros["PSA"] >= pq[1,9]]
    PSA_outliers = h2o.assign(PSA_outliers, "PSA.outliers")
    pros.head(show=True)
    PSA_outliers.head(show=True)
    assert PSA_outliers._id == "PSA.outliers", "Expected frame id to be PSA.outliers, but got {0}".format(PSA_outliers._id)


pyunit_test = pyunit_assign
