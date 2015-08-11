import sys
sys.path.insert(1, "../../../")
import h2o

def pca_scoring(ip, port):
    

    print "Importing arrests.csv data..."
    arrestsH2O = h2o.upload_file(h2o.locate("smalldata/pca_test/USArrests.csv"))

    print "Run PCA with transform = 'DEMEAN'"
    fitH2O = h2o.prcomp(x=arrestsH2O[0:4], k = 4, transform = "DEMEAN")
    # TODO: fitH2O.show()

    print "Project training data into eigenvector subspace"
    predH2O = fitH2O.predict(arrestsH2O)
    print "H2O Projection:"
    print predH2O.head()

if __name__ == "__main__":
    h2o.run_test(sys.argv, pca_scoring)
