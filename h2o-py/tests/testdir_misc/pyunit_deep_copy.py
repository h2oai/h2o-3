from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def pyunit_deep_copy():

    pros_1 = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    pros_2 = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    pros_copy_1 = h2o.deep_copy(pros_1, "copy")
    pros_copy_2 = h2o.deep_copy(pros_2, "copy2")

    #Change a part of the original frame and a copied frame. It is expected in a deep copy that changing the original
    #frame will not effect the duplicate and vice versa
    pros_1.insert_missing_values()
    pros_copy_2.insert_missing_values()

    print("Original Frame with inserted missing values:")
    print(pros_1)
    print("Duplicate Frame with no inserted missing values")
    print(pros_copy_1)
    print("Original Frame with no inserted missing values:")
    print(pros_2)
    print("Duplicate Frame with inserted missing values")
    print(pros_copy_2)
    print("Number of frames in session after deep_copy")
    print(h2o.ls())

    assert pros_1.nacnt() != pros_copy_1.nacnt() , "Inserted NA's into the original frame but the original seems to match the duplicates NA count!"
    assert pros_2.nacnt() != pros_copy_2.nacnt() , "Inserted NA's into the duplicate frame but the original seems to match the originals NA count!"



if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_deep_copy)
else:
    pyunit_deep_copy()