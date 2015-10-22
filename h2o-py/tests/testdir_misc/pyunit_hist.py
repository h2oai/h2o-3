import sys
sys.path.insert(1, "../../")
import h2o, tests

def hist_test():
    
    

    kwargs = {}
    kwargs['server'] = True

    print "Import small prostate dataset"
    hex = h2o.import_file(h2o.locate("smalldata/logreg/prostate.csv"))
    hex["AGE"].hist(**kwargs)
    hex["VOL"].hist(**kwargs)

if __name__ == "__main__":
    tests.run_test(sys.argv, hist_test)
