import sys
sys.path.insert(1, "../../../")
import h2o, tests

def pca_arrests(ip, port):
    

    print "Importing USArrests.csv data..."
    arrestsH2O = h2o.upload_file(h2o.locate("smalldata/pca_test/USArrests.csv"))
    arrestsH2O.describe()

    for i in range(4):
        print "H2O PCA with " + str(i) + " dimensions:\n"
        print "Using these columns: {0}".format(arrestsH2O.names)
        pca_h2o = h2o.prcomp(x=arrestsH2O[0:4], k = i+1)
        # TODO: pca_h2o.show()

if __name__ == "__main__":
    tests.run_test(sys.argv, pca_arrests)
