



def countmatches_check():
    # Connect to a pre-existing cluster

    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"), col_types=["numeric","numeric","numeric","numeric","string"])

    # single column (frame)
    result = frame["C5"].countmatches("o")
    assert result.nrow == 150 and result.ncol == 1
    assert result[0,0] == 1 and result[50,0] == 2 and result[100,0] == 0, "Expected 1, 2, 0 but got {0}, {1}, and " \
                                                              "{2}".format(result[0,0], result[50,0], result[100,0])

    # single column (vec)
    vec = frame["C5"]
    result = vec.countmatches("ic")
    assert result.nrow == 150 and result.ncol == 1
    assert result[0,0] == 0 and result[50,0] == 1 and result[100,0] == 1, "Expected 0, 1, 1 but got {0}, {1}, and " \
                                                              "{2}".format(result[0,0], result[50,0], result[100,0])

    # array of targets 
    vec = frame["C5"]
    result = vec.countmatches(["ic","ri", "ca"])
    assert result.nrow == 150 and result.ncol == 1
    assert result[0,0] == 1 and result[50,0] == 2 and result[100,0] == 3, "Expected 1, 2, 3 but got {0}, {1}, and " \
                                                              "{2}".format(result[0,0], result[50,0], result[100,0])




countmatches_check()
