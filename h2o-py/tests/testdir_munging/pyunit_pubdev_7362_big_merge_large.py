import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def check_big_merge():
    h2o.remove_all()
    nrow = 1000000
    ncol = 2
    iRange = 100000
    frame1 = h2o.create_frame(rows=nrow, cols=ncol, integer_fraction=1, seed = 12345, integer_range = iRange, missing_fraction=0.0)
    frame2 = h2o.create_frame(rows = nrow, cols = ncol, integer_fraction=1, seed = 54321, integer_range = iRange, missing_fraction=0.0)

    frame1.set_names(["C1","C2"])
    frame2.set_names(["C1","C3"])

    mergedExact = frame1.merge(frame2, by_x=["C1"],by_y=["C1"], all_x=False, all_y=False)    
    mergedLeft = frame1.merge(frame2,by_x=["C1"],by_y=["C1"], all_x=True)

    assert mergedExact.nrow < mergedLeft.nrow, "Expected row numbers are wrong"
 
if __name__ == "__main__":
    pyunit_utils.standalone_test(check_big_merge)
else:
    check_big_merge()
