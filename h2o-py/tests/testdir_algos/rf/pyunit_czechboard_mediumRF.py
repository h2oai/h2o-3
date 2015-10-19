

import h2o, tests

def czechboardRF():

    
    

    # Training set has checkerboard pattern
    board = h2o.import_file(path=tests.locate("smalldata/gbm_test/czechboard_300x300.csv"))
    board["C3"] = board["C3"].asfactor()
    board.summary()

    # Train H2O DRF Model:
    model = h2o.random_forest(x=board[["C1", "C2"]], y=board["C3"], ntrees=50, max_depth=20, nbins=500)
    model.show()
  

pyunit_test = czechboardRF
