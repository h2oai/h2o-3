import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def impute():
    # Connect to a pre-existing cluster
    

    prostate = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate_missing.csv"))
    prostate.dim

    #print "Summary of the data in iris_missing.csv"
    #print "Each column has 50 missing observations (at random)"
    #prostate.summary()

    #print "Make a copy of the original dataset to play with."

    print "Impute a numeric column with the mean"
    nas = prostate["DPROS"].isna().sum()
    print "NAs before imputation: {0}".format(nas)
    prostate.impute("DPROS", method="mean")

    nas = prostate["DPROS"].isna().sum()
    print "NAs after imputation: {0}".format(nas)

    # OTHER POSSIBLE SYNTAXES ALLOWED:
    prostate = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate_missing.csv"))
    prostate.impute(8, method="mean")

    prostate = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate_missing.csv"))
    prostate.impute( "VOL", method="mean")

    # USING  MEDIAN
    print "Impute a numeric column with the median"

    prostate = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate_missing.csv"))
    prostate.impute("VOL", method="median")

    prostate = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate_missing.csv"))
    prostate.impute(8, method="median")

    prostate = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate_missing.csv"))
    prostate.impute("VOL", method="median")



if __name__ == "__main__":
    pyunit_utils.standalone_test(impute)
else:
    impute()
