import sys
sys.path.insert(1, "../../")
import h2o

def colname_set_basic(ip,port):
    # Connect to h2o
    

    print "Uploading iris data..."

    no_headers = h2o.upload_file(h2o.locate("smalldata/iris/iris.csv"))
    headers_and = h2o.upload_file(h2o.locate("smalldata/iris/iris_header.csv"))

    print no_headers.names()
    print headers_and.names()

    no_headers.setNames(headers_and.names())
    assert no_headers.names() == headers_and.names(), "Expected the same column names but got {0} and {1}".\
        format(no_headers.names(), headers_and.names())

if __name__ == "__main__":
    h2o.run_test(sys.argv, colname_set_basic)
