



def pca_arrests():
    

    print "Importing USArrests.csv data..."
    arrestsH2O = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
    arrestsH2O.describe()

    for i in range(4):
        print "H2O PCA with " + str(i) + " dimensions:\n"
        print "Using these columns: {0}".format(arrestsH2O.names)
        pca_h2o = h2o.prcomp(x=arrestsH2O[0:4], k = i+1)
        # TODO: pca_h2o.show()


pca_arrests()
