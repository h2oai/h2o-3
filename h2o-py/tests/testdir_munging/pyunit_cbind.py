#!/usr/bin/python
# -*- encoding: utf-8 -*-
import h2o
from h2o.exceptions import H2OTypeError, H2OValueError
from tests import pyunit_utils


def test_cbind():
    """Test H2OFrame.cbind() method."""

    hdf = h2o.import_file(path=pyunit_utils.locate('smalldata/jira/pub-180.csv'))
    otherhdf = h2o.import_file(path=pyunit_utils.locate('smalldata/jira/v-11.csv'))
    rows, cols = hdf.shape
    assert rows == 12 and cols == 4, "unexpected dimensions in original"

    ##################################
    #     non-mutating h2o.cbind     #
    ##################################
    # frame to frame
    hdf2 = hdf.cbind(hdf)
    rows2, cols2 = hdf2.dim
    assert hdf2.shape == (12, 8)
    print(hdf2.frame_id)
    assert hdf2.shape == (12, 8)

    # vec to vec
    xx = hdf[0]
    yy = hdf[1]
    hdf3 = xx.cbind(yy)
    assert hdf3.shape == (12, 2)
    assert hdf3.names == ['colgroup', 'colgroup2']
    print(hdf3.frame_id)
    assert hdf3.shape == (12, 2)
    assert hdf3.names == ['colgroup', 'colgroup2']

    # vec to frame
    hdf4 = hdf.cbind(yy)
    hdf5 = yy.cbind(hdf)
    assert hdf4.shape == hdf5.shape == (12, 5)

    # logical expressions
    hdf6 = (hdf[2] <= 5).cbind(hdf[3] >= 4)
    assert hdf6.shape == (12, 2)

    # unequal rows should fail
    try:
        hdf.cbind(otherhdf)
        assert False, "Expected an error"
    except H2OValueError:
        pass

    # cbinding of wrong types should fail
    try:
        hdf.cbind("hello")
        assert False
    except H2OTypeError:
        pass

    try:
        hdf.cbind([hdf, {"x": hdf}])
        assert False
    except H2OTypeError:
        pass

    # cbinding of multiple columns
    hdf7 = xx.cbind([xx, xx, xx])
    assert hdf7.shape == (12, 4)
    print(hdf7.frame_id)
    assert hdf7.shape == (12, 4)

    # cbinding of constants
    hdf8 = xx.cbind([1, -1])
    assert hdf8.shape == (12, 3)
    print(hdf8.frame_id)
    assert hdf8.shape == (12, 3)


    ###################################
    #     mutating H2OFrame.cbind     #
    ###################################

    # frame to frame
    hdf = hdf.cbind(hdf)
    assert hdf.shape == (12, 8)
    print(hdf.frame_id)
    assert hdf.shape == (12, 8)

    # frame to vec
    hdf = hdf.cbind(yy)
    assert hdf.shape == (12, 9)
    print(hdf.frame_id)
    assert hdf.shape == (12, 9)

    # logical expressions
    hdf = hdf.cbind(hdf[2] <= 5)
    assert hdf.shape == (12, 10)
    assert hdf.names == ['colgroup', 'colgroup2', 'col1', 'col2',
                         'colgroup0', 'colgroup20', 'col10', 'col20', 'colgroup21', 'col11']
    print(hdf.frame_id)
    assert hdf.shape == (12, 10)



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_cbind)
else:
    test_cbind()
