import sys
sys.path.insert(1, "../../../")
import h2o

def czechboardRF(ip,port):

    # Connect to h2o
    h2o.init(ip,port)

    # Training set has checkerboard pattern
    #Log.info("Importing czechboard_300x300.csv data...\n")
    board = h2o.import_frame(path=h2o.locate("smalldata/gbm_test/czechboard_300x300.csv"))

    board["C3"] = board["C3"].asfactor()
    #Log.info("Summary of czechboard_300x300.csv from H2O:\n")
    #board.summary()

    # Train H2O DRF Model:
    #Log.info("H2O DRF (Naive Split) with parameters:\nclassification = TRUE, ntree = 50, depth = 20, nbins = 500\n")
    model = h2o.random_forest(x=board[["C1", "C2"]], y=board["C3"], ntrees=50, max_depth=20, nbins=500)
    model.show()
  
if __name__ == "__main__":
  h2o.run_test(sys.argv, czechboardRF)
