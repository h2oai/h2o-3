



def pca_scoring():


    print "Importing arrests.csv data..."
    arrestsH2O = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))

    print "Run PCA with transform = 'DEMEAN'"
    fitH2O = h2o.prcomp(x=arrestsH2O[0:4], k = 4, transform = "DEMEAN")
    # TODO: fitH2O.show()

    print "Project training data into eigenvector subspace"
    predH2O = fitH2O.predict(arrestsH2O)
    print "H2O Projection:"
    predH2O.head()


pca_scoring()
