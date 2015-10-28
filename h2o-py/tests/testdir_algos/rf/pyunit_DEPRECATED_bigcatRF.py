import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def bigcatRF():
    
    

    # Training set has 100 categories from cat001 to cat100
    # Categories cat001, cat003, ... are perfect predictors of y = 1
    # Categories cat002, cat004, ... are perfect predictors of y = 0

    #Log.info("Importing bigcat_5000x2.csv data...\n")
    bigcat = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/bigcat_5000x2.csv"))
    bigcat["y"] = bigcat["y"].asfactor()

    #Log.info("Summary of bigcat_5000x2.csv from H2O:\n")
    #bigcat.summary()

    # Train H2O DRF Model:
    #Log.info("H2O DRF (Naive Split) with parameters:\nclassification = TRUE, ntree = 1, depth = 1, nbins = 100, nbins_cats=10\n")
    model = h2o.random_forest(x=bigcat[["X"]], y=bigcat["y"], ntrees=1, max_depth=1, nbins=100, nbins_cats=10)
    model.show()



if __name__ == "__main__":
    pyunit_utils.standalone_test(bigcatRF)
else:
    bigcatRF()
