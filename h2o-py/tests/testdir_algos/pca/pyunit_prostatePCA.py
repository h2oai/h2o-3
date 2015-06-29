import sys
sys.path.insert(1, "../../../")
import h2o

def pca_prostate(ip, port):
    h2o.init(ip, port)

    print "Importing prostate.csv data...\n"
    prostate = h2o.upload_file(h2o.locate("smalldata/logreg/prostate.csv"))

    print "Converting CAPSULE, RACE, DPROS and DCAPS columns to factors"
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
    prostate["RACE"] = prostate["RACE"].asfactor()
    prostate["DPROS"] = prostate["DPROS"].asfactor()
    prostate["DCAPS"] = prostate["DCAPS"].asfactor()
    prostate.describe()

    print "PCA on columns 3 to 9 with k = 3, retx = FALSE, transform = 'STANDARDIZE'"
    fitPCA = h2o.prcomp(x=prostate[2:9], k=3, transform="NONE", pca_method="Power")
    pred1 = fitPCA.predict(prostate)
    pred2 = h2o.get_frame(fitPCA._model_json['output']['loading_key']['name'])

    print "Compare dimensions of projection and loading matrix"
    print "Projection matrix:\n"
    print pred1.head()
    print "Loading matrix:\n"
    print pred2.head()
    assert pred1.nrow() == pred2.nrow(), "Expected same number of rows, but got {0} and {1}".format(pred1.nrow(),
                                                                                                    pred2.nrow())
    assert pred1.ncol() == pred2.ncol(), "Expected same number of rows, but got {0} and {1}".format(pred1.ncol(),
                                                                                                    pred2.ncol())

if __name__ == "__main__":
    h2o.run_test(sys.argv, pca_prostate)
