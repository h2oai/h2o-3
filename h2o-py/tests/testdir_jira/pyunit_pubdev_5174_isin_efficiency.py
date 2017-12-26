import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from sets import Set

from h2o.exceptions import H2OResponseError
from tests import pyunit_utils


def pubdev_5174():

    x = h2o.import_file(pyunit_utils.locate('smalldata/jira/PUBDEV-5174.csv'), header = 1)

    tt = x['rr'].unique()
    gg = tt[:10000, 0]

    ww = x[~x['rr'].isin(gg['C1'].ascharacter().as_data_frame()['C1'].tolist())]
    
    print(x.nrow)
    print(tt.nrow)
    print(ww.nrow)

    assert x.nrow == 1000000, "Original data has 1000000 rows"
    assert tt.nrow == 499851, "Column rr has 499851 unique values"
    assert ww.nrow == 979992, "Original data reduced has 979992 rows" # TODO: how can this be > 1000000 - 499851?
    
    # What do we do with Sets and Tuples?
    
    # there are 2 instances of 'cTeYX' and 2 of 'Todxf'
    tup = ('cTeYX', 'Todxf')
    ww_tuple = x[~x['rr'].isin(tup)]
    assert ww_tuple.nrow == 999996, "Original data reduced has 999996 rows"

    s = Set(['cTeYX'])
    ww_set = x[~x['rr'].isin(list(s))] # no, we don't handle Set; put it on the client to convert
    assert ww_set.nrow == 999998, "Original data reduced has 999998 rows"
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5174)
else:
    pubdev_5174()
