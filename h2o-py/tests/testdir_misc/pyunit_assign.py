import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def pyunit_assign():

    pros = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    pq = pros.quantile()
    print '1st percentile for PSA:', pq[0,7]
    print '99th percentile for PSA:', pq[8,7]
    PSA_outliers = pros[ ((pros["PSA"] <= pq[0,7]) | (pros["PSA"] >= pq[8,7])) ]
    PSA_outliers = h2o.assign(PSA_outliers, "PSA.outliers")
    print pros
    print PSA_outliers
    assert PSA_outliers.frame_id == "PSA.outliers", "Expected frame id to be PSA.outliers, but got {0}".format(PSA_outliers.frame_id)



if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_assign)
else:
    pyunit_assign()