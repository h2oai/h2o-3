import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


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
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5174)
else:
    pubdev_5174()
