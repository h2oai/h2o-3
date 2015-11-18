import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def czechboardRF():

    
    

    # Training set has checkerboard pattern
    board = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/czechboard_300x300.csv"))
    board["C3"] = board["C3"].asfactor()
    board.summary()

    # Train H2O DRF Model:
    model = h2o.random_forest(x=board[["C1", "C2"]], y=board["C3"], ntrees=50, max_depth=20, nbins=500)
    model.show()
  


if __name__ == "__main__":
    pyunit_utils.standalone_test(czechboardRF)
else:
    czechboardRF()
