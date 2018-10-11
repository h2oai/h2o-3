from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def verify_merge():
    nrow = 10000
    ncol = 3
    seed1 = 12345
    seed2 = 54321
    cardinality = 2*nrow
    integerR = 100000000
    frame1_enum = pyunit_utils.random_dataset_enums_only(nrow, ncol, factorL=cardinality, misFrac=0, randSeed=seed1)
    frame1_str = pyunit_utils.random_dataset_strings_only(nrow, ncol, seed=seed1)
    frame1_int = pyunit_utils.random_dataset_int_only(nrow, ncol, rangeR=integerR, misFrac=0, randSeed=seed1)
    frame1 = frame1_int.cbind(frame1_str.cbind(frame1_enum))
    frame2_enum = pyunit_utils.random_dataset_enums_only(nrow, ncol, factorL=cardinality, misFrac=0, randSeed=seed2)
    frame2_str = pyunit_utils.random_dataset_strings_only(nrow, ncol, seed=seed2)
    frame2_int = pyunit_utils.random_dataset_int_only(nrow, ncol, rangeR=integerR, misFrac=0, randSeed=seed2)
    frame2 = frame2_int.cbind(frame2_str.cbind(frame2_enum))

    # merge one column only
    frame1.set_names(["f1_1", "f1_2", "f1_3", "f1_4", "f1_5", "f1_6", "enum1", "f1_8", "f1_9"])
    frame2.set_names(["f2_1", "f2_2", "f2_3", "f2_4", "f2_5", "f2_6", "enum1", "f28", "f2_9"])
    perform_merges_assert_correct_merge(frame1, frame2)

    # merge two columns
    frame1.set_names(["f1_1", "f1_2", "f1_3", "f1_4", "f1_5", "f1_6", "enum1", "enum2", "f1_9"])
    frame2.set_names(["f2_1", "f2_2", "f2_3", "f2_4", "f2_5", "f2_6", "enum1", "enum2", "f2_9"])
    perform_merges_assert_correct_merge(frame1, frame2)

    # merge three columns
    frame1.set_names(["f1_1", "f1_2", "f1_3", "f1_4", "f1_5", "f1_6", "enum1", "enum2", "enum3"])
    frame2.set_names(["f2_1", "f2_2", "f2_3", "f2_4", "f2_5", "f2_6", "enum1", "enum2", "enum3"])
    perform_merges_assert_correct_merge(frame1, frame2)


def perform_merges_assert_correct_merge(frame1, frame2):
    mergeKeepLeft = frame1.merge(frame2, all_x = True) # should equal to mergeKeepRight2
    mergeKeepRight2 = frame2.merge(frame1, all_y=True)
    assert_equal_frames(mergeKeepLeft, mergeKeepRight2, "f1_1")

    mergeKeepRight = frame1.merge(frame2, all_y=True) # should equal to mergeLeft2
    mergeKeepLeft2 = frame2.merge(frame1, all_x = True)
    assert_equal_frames(mergeKeepRight, mergeKeepLeft2, "f1_1")

    # merge right, left should all have equal NaNs in them
    assert total_na_cnts(mergeKeepRight)==total_na_cnts(mergeKeepLeft2), \
        "Na counts should equal but frame 1 has {0} and frame 2 has {1}".format(mergeKeepRight.nacnt(),
                                                                                mergeKeepLeft2.nacnt())
    assert total_na_cnts(mergeKeepRight2)==total_na_cnts(mergeKeepLeft), \
        "Na counts should equal but frame 1 has {0} and frame 2 has {1}".format(mergeKeepRight2.nacnt(),
                                                                                mergeKeepLeft.nacnt())

def assert_equal_frames(f1, f2, sortColName):
    f1sorted = f1.sort(sortColName)
    f2sorted = f2.sort(sortColName)

    pyunit_utils.compare_frames_equal_names(f1sorted, f2sorted)

def total_na_cnts(fr):
    na_list = fr.nacnt()
    sum=0.0
    for ele in na_list:
        sum=sum+ele
    return sum



if __name__ == "__main__":
    pyunit_utils.standalone_test(verify_merge)
else:
    verify_merge()

