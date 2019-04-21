from __future__ import print_function
import sys

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
import random

def import_zip_skipped_columns():
    # checking out zip file
    airlineFull = h2o.import_file(path=pyunit_utils.locate("smalldata/jira/adult.gz"))
    filePath = pyunit_utils.locate("smalldata/jira/adult.gz")

    skip_all = list(range(airlineFull.ncol))
    skip_even = list(range(0, airlineFull.ncol, 2))
    skip_odd = list(range(1, airlineFull.ncol, 2))
    skip_start_end = [0, airlineFull.ncol - 1]
    skip_except_last = list(range(0, airlineFull.ncol - 2))
    skip_except_first = list(range(1, airlineFull.ncol))
    temp = list(range(0, airlineFull.ncol))
    random.shuffle(temp)
    skip_random = []
    for index in range(0, airlineFull.ncol // 2):
        skip_random.append(temp[index])
    skip_random.sort()

    try:
        bad = h2o.import_file(filePath, skipped_columns=skip_all)  # skipped all
        sys.exit(1)
    except Exception as ex:
        print(ex)
        pass

    try:
        bad = h2o.upload_file(filePath, skipped_columns=skip_all)   # skipped all
        sys.exit(1)
    except Exception as ex:
        print(ex)
        pass

        # skip odd columns
    pyunit_utils.checkCorrectSkips(airlineFull, filePath, skip_odd)

    # skip even columns
    pyunit_utils.checkCorrectSkips(airlineFull, filePath, skip_even)

    # skip the very beginning and the very end.
    pyunit_utils.checkCorrectSkips(airlineFull, filePath, skip_start_end)

    # skip all except the last column
    pyunit_utils.checkCorrectSkips(airlineFull, filePath, skip_except_last)

    # skip all except the very first column
    pyunit_utils.checkCorrectSkips(airlineFull, filePath, skip_except_first)

    # randomly skipped half the columns
    pyunit_utils.checkCorrectSkips(airlineFull, filePath, skip_random)


def checkCorrectSkips(originalFullFrame, csvfile, skipped_columns):
    skippedFrameUF = h2o.upload_file(csvfile, skipped_columns=skipped_columns)
    skippedFrameIF = h2o.import_file(csvfile, skipped_columns=skipped_columns)  # this two frames should be the same
    pyunit_utils.compare_frames_local(skippedFrameUF, skippedFrameIF, prob=0.5)

    skipCounter = 0
    typeDict = originalFullFrame.types
    frameNames = originalFullFrame.names
    for cindex in range(len(frameNames)):
        if cindex not in skipped_columns:
            print("Checking column {0}...".format(cindex))
            if typeDict[frameNames[cindex]] == u'enum' and cindex==10: # look at original frame
                continue

            elif typeDict[frameNames[cindex]] == u'enum' and not(skipCounter==10):
                pyunit_utils.compare_frames_local_onecolumn_NA_enum(originalFullFrame[cindex],
                                                                    skippedFrameIF[skipCounter], prob=1, tol=1e-10,
                                                                    returnResult=False)
            elif typeDict[frameNames[cindex]] == u'string':
                pyunit_utils.compare_frames_local_onecolumn_NA_string(originalFullFrame[cindex],
                                                         skippedFrameIF[skipCounter], prob=1,
                                                         returnResult=False)
            elif typeDict[frameNames[cindex]] == u'int':
                pyunit_utils.compare_frames_local_onecolumn_NA(originalFullFrame[cindex], skippedFrameIF[skipCounter].asnumeric(),
                                                  prob=1, tol=1e-10, returnResult=False)
            skipCounter = skipCounter + 1

if __name__ == "__main__":
    pyunit_utils.standalone_test(import_zip_skipped_columns)
else:
    import_zip_skipped_columns()
