from tests import pyunit_utils
import sys
sys.path.insert(1, "../../../")
import h2o

def czechboardRF():

    # Connect to h2o
    h2o.init(ip,port)

    # Training set has checkerboard pattern
    #Log.info("Importing czechboard_300x300.csv data...\n")
    board = h2o.import_frame(path=pyunit_utils.locate("smalldata/gbm_test/czechboard_300x300.csv"))


    board["C3"] = board["C3"].asfactor()
    #Log.info("Summary of czechboard_300x300.csv from H2O:\n")
    #board.summary()

    # Train H2O DRF Model:
    #Log.info("H2O DRF (Naive Split) with parameters:\nclassification = TRUE, ntree = 50, depth = 20, nbins = 500\n")
    model = h2o.random_forest(x=board[["C1", "C2"]], y=board["C3"], ntrees=50, max_depth=20, nbins=500)
    model.show()
  
if __name__ == "__main__":
	pyunit_utils.standalone_test(czechboardRF)
else:
	czechboardRF()
