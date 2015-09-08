import sys
sys.path.insert(1, "../../")
import h2o, tests

def headers():
    
    

    headers = h2o.import_file(h2o.locate("smalldata/airlines/allyears2k_headers_only.csv"))
    headers_and = h2o.import_file(h2o.locate("smalldata/airlines/allyears2k.zip"))
<<<<<<< HEAD
    headers_and.set_names(headers.names)
=======
    headers_and.setNames(headers.names)
>>>>>>> master
    print headers.names
    print headers_and.names
    assert headers.names == headers_and.names, "Expected the same column names but got {0} and {1}". \
        format(headers.names, headers_and.names)

if __name__ == "__main__":
    tests.run_test(sys.argv, headers)
