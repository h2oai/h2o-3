



def http_import():
    
    

    url = "http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip"
    aa = h2o.import_file(path=url)
    aa.show()


http_import()
