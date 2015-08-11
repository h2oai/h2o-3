import sys
sys.path.insert(1, "../../")
import h2o

def pyunit_assign(ip,port):

    pros = h2o.import_frame(h2o.locate("smalldata/prostate/prostate.csv"))
    pq = pros.quantile()

    PSA_outliers = pros[pros["PSA"] <= pq[1,1] or pros["PSA"] >= pq[1,9]]
    PSA_outliers = h2o.assign(PSA_outliers, "PSA.outliers")
    pros.head(show=True)
    PSA_outliers.head(show=True)
    assert PSA_outliers._id == "PSA.outliers", "Expected frame id to be PSA.outliers, but got {0}".format(PSA_outliers._id)

if __name__ == "__main__":
    h2o.run_test(sys.argv, pyunit_assign)
