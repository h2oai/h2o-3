import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def upload_import():
    # Connect to a pre-existing cluster
    

    uploaded_frame = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))
    imported_frame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))

    rows_u, cols_u = uploaded_frame.dim
    rows_i, cols_i = imported_frame.dim

    assert rows_u == rows_i, "Expected same number of rows regardless of method. upload: {0}, import: " \
                             "{1}.".format(rows_u, rows_i)

    assert cols_u == cols_i, "Expected same number of cols regardless of method. upload: {0}, import: " \
                             "{1}.".format(cols_u, cols_i)



if __name__ == "__main__":
    pyunit_utils.standalone_test(upload_import)
else:
    upload_import()
