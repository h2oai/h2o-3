

import h2o, tests

def headers():
    
    

    headers = h2o.import_file(tests.locate("smalldata/airlines/allyears2k_headers_only.csv"))
    headers_and = h2o.import_file(tests.locate("smalldata/airlines/allyears2k.zip"))
    headers_and.set_names(headers.names)
    print headers.names
    print headers_and.names
    assert headers.names == headers_and.names, "Expected the same column names but got {0} and {1}". \
        format(headers.names, headers_and.names)


pyunit_test = headers
