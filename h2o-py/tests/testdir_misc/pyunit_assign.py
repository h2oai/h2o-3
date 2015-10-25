import sys
sys.path.insert(1, "../../")
import h2o, tests

def pyunit_assign():

    pros = h2o.import_file(tests.locate("smalldata/prostate/prostate.csv"))
    pq = pros.quantile()

    PSA_outliers = pros[pros["PSA"] <= pq[1,1] or pros["PSA"] >= pq[1,9]]
    PSA_outliers = h2o.assign(PSA_outliers, "PSA.outliers")
    pros.head().show()
    PSA_outliers.head().show()
    assert PSA_outliers.frame_id == "PSA.outliers", "Expected frame id to be PSA.outliers, but got {0}".format(PSA_outliers.frame_id)

if __name__ == "__main__":
    tests.run_test(sys.argv, pyunit_assign)
