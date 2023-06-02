import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import random

def sortOrMerge():
    # PUBDEV-5266 sort/merge with string columns but not on string columns
    # test either the merge or the sort part
    name1 = "bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/PUBDEV_5266_f1.csv"
    name2 = "bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/PUBDEV_5266_f2.csv"
    c1names = ["stringf1-1", "stringf1-2", "int1", "intf1-1"]
    c2names = ["stringf2-1","intf2-1", "iintf2-2", "stringf2-2","intf2-3",  "stringf2-3", "stringf2-4",  "int1"]
    f1names = [name1, name1, name1]
    f2names = [name2, name2, name2]
    ansNames = ["bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/sortedF1_R_C3_C4.csv",
                "bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/mergedf1_f2unique.csv",
                "bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/mergedf1_f2unique_x_T.csv"]
    xvals = [False,False,True]
    yvals = [False,False,False]
    f1colnames = [c1names, c1names, c1names]
    f2colnames = [c2names, c2names, c2names]
    numTests = len(xvals)-1
    runIndex = random.randint(0,numTests)

    if runIndex==0: # perform sorting first
        f1 = h2o.import_file(pyunit_utils.locate(f1names[runIndex]))
        sorted_column_indices = [2, 3]
        h2oSortf1 = f1.sort(sorted_column_indices)
        coltypes = getTypes(h2oSortf1)
        f1sortedR = h2o.import_file(pyunit_utils.locate(ansNames[runIndex]), col_types=coltypes, header=1)
        assert pyunit_utils.compare_frames(f1sortedR, h2oSortf1, 100, tol_numeric=0)
    else:   # test merging here
        f1 = h2o.import_file(pyunit_utils.locate(f1names[runIndex]),header=1)
        f1.set_names(f1colnames[runIndex])
        f2 = h2o.import_file(pyunit_utils.locate(f2names[runIndex]),header=1)
        f2.set_names(f2colnames[runIndex])
        mergedh2o = f1.merge(f2,all_x=xvals[runIndex],all_y=yvals[runIndex], method='auto')
        coltypes = getTypes(mergedh2o)
        f1mergedf2 = h2o.import_file(pyunit_utils.locate(ansNames[runIndex]), col_types=coltypes, header=1)
        assert pyunit_utils.compare_frames(f1mergedf2, mergedh2o, 100, tol_numeric=0)

def getTypes(frame):
    colTypes = []
    for index in range(frame.ncol):
        colTypes.append(str(frame.type(index)))
    return colTypes

if __name__ == "__main__":
    pyunit_utils.standalone_test(sortOrMerge)
else:
    sortOrMerge()

