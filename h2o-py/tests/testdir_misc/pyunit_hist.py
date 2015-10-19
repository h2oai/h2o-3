

import h2o, tests

def hist_test():
    
    

    kwargs = {}
    kwargs['server'] = True

    print "Import small prostate dataset"
    hex = h2o.import_file(tests.locate("smalldata/logreg/prostate.csv"))
    hex["AGE"].hist(**kwargs)
    hex["VOL"].hist(**kwargs)


pyunit_test = hist_test
