import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def pca_prostate():


    print "Importing prostate.csv data...\n"
    prostate = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    print "Converting CAPSULE, RACE, DPROS and DCAPS columns to factors"
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
    prostate["RACE"] = prostate["RACE"].asfactor()
    prostate["DPROS"] = prostate["DPROS"].asfactor()
    prostate["DCAPS"] = prostate["DCAPS"].asfactor()
    prostate.describe()

    print "PCA on columns 3 to 9 with k = 3, retx = FALSE, transform = 'STANDARDIZE'"
    fitPCA = h2o.prcomp(x=prostate[2:9], k=3, transform="NONE", pca_method="Power")
    pred = fitPCA.predict(prostate)

    print "Projection matrix:\n"
    pred.head()



if __name__ == "__main__":
    pyunit_utils.standalone_test(pca_prostate)
else:
    pca_prostate()
