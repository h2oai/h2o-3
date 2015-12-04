from tests import pyunit_utils
import sys
sys.path.insert(1, "../../")
import h2o

def headers():
    # Connect to h2o
    h2o.init(ip,port)

    headers = h2o.import_frame(pyunit_utils.locate("smalldata/airlines/allyears2k_headers_only.csv"))

    headers_and = h2o.import_frame(pyunit_utils.locate("smalldata/airlines/allyears2k.zip"), col_names=headers)

    print headers.names()
    print headers_and.names()
    assert headers.names() == headers_and.names(), "Expected the same column names but got {0} and {1}". \
        format(headers.names(), headers_and.names())

if __name__ == "__main__":
	pyunit_utils.standalone_test(headers)
else:
	headers()
