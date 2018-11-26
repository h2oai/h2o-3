from __future__ import print_function
import sys

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
import os
import datetime
import random

MINTIME = datetime.datetime(1980, 8, 6, 6, 14, 59)
MAXTIME = datetime.datetime(2080, 8, 6, 8, 14, 59)


def test_arff_parser_column_skip():
    # generate a big frame with all datatypes and save it to svmlight
    nrow = 10000
    ncol = 98
    seed = 12345
    frac1 = 0.16
    frac2 = 0.2
    f1 = h2o.create_frame(rows=nrow, cols=ncol, real_fraction=frac1, categorical_fraction=frac1, integer_fraction=frac1,
                          binary_fraction=frac1, time_fraction=frac1, string_fraction=frac2, missing_fraction=0.1,
                          has_response=False, seed=seed)
    uuidVecs = [pyunit_utils.gen_random_uuid(nrow), pyunit_utils.gen_random_uuid(nrow)]  # generate the uuid vectos
    tmpdir = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results"))
    if not (os.path.isdir(tmpdir)):
        os.mkdir(tmpdir)
    savefilenamewithpath = os.path.join(tmpdir, 'out.arff')
    uuidNames = ["uuidVec1", "uuidVec2"]
    pyunit_utils.write_H2OFrame_2_ARFF(savefilenamewithpath, "out.arff", f1, uuidVecs,
                                       uuidNames)  # write h2o frame to svm format

    ncol = f1.ncol + len(uuidVecs)
    skip_all = list(range(ncol))
    skip_even = list(range(0, ncol, 2))
    skip_odd = list(range(1, ncol, 2))
    skip_start_end = [0, ncol - 1]
    skip_except_last = list(range(0, ncol - 2))
    skip_except_first = list(range(1, ncol))
    temp = list(range(0, ncol))
    random.shuffle(temp)
    skip_random = []
    for index in range(0, ncol // 2):
        skip_random.append(temp[index])
    skip_random.sort()

    try:
        loadFileSkipAll = h2o.upload_file(savefilenamewithpath, skipped_columns=skip_all)
        sys.exit(1)  # should have failed here
    except:
        pass

    try:
        importFileSkipAll = h2o.import_file(savefilenamewithpath, skipped_columns=skip_all)
        sys.exit(1)  # should have failed here
    except:
        pass

    # skip even columns
    checkCorrectSkips(f1, savefilenamewithpath, skip_even, uuidNames)

    # skip odd columns
    checkCorrectSkips(f1, savefilenamewithpath, skip_odd, uuidNames)

    # skip the very beginning and the very end.
    checkCorrectSkips(f1, savefilenamewithpath, skip_start_end, uuidNames)

    # skip all except the last column
    checkCorrectSkips(f1, savefilenamewithpath, skip_except_last, uuidNames)

    # skip all except the very first column
    checkCorrectSkips(f1, savefilenamewithpath, skip_except_first, uuidNames)

    # randomly skipped half the columns
    checkCorrectSkips(f1, savefilenamewithpath, skip_random, uuidNames)


def checkCorrectSkips(originalFullFrame, csvfile, skipped_columns, uuidNames):
    skippedFrameUF = h2o.upload_file(csvfile, skipped_columns=skipped_columns)
    skippedFrameIF = h2o.import_file(csvfile, skipped_columns=skipped_columns)  # this two frames should be the same
    pyunit_utils.compare_frames_local(skippedFrameUF, skippedFrameIF, prob=0.5)

    skipCounter = 0
    typeDict = originalFullFrame.types
    frameNames = originalFullFrame.names
    for cindex in range(len(frameNames)):
        if cindex not in skipped_columns:
            if typeDict[frameNames[cindex]] == u'enum':
                pyunit_utils.compare_frames_local_onecolumn_NA_enum(originalFullFrame[cindex],
                                                                    skippedFrameIF[skipCounter], prob=1, tol=1e-10,
                                                                    returnResult=False)
            elif typeDict[frameNames[cindex]] == u'string':
                pyunit_utils.compare_frames_local_onecolumn_NA_string(originalFullFrame[cindex],
                                                                      skippedFrameIF[skipCounter], prob=1,
                                                                      returnResult=False)
            else:
                pyunit_utils.compare_frames_local_onecolumn_NA(originalFullFrame[cindex], skippedFrameIF[skipCounter],
                                                               prob=1, tol=1e-10, returnResult=False)
            skipCounter = skipCounter + 1

    # since we cannot check uuid contents, we at least need to know that the return frame contains the correct column names
    frameNames.extend(uuidNames)
    skippedFrameNames = skippedFrameIF.names

    for skipIndex in skipped_columns:
        assert frameNames[skipIndex] not in skippedFrameNames, \
            "This column: {0}/{1} should have been skipped but is not!".format(frameNames[skipIndex], skipIndex)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_arff_parser_column_skip)
else:
    test_arff_parser_column_skip()
