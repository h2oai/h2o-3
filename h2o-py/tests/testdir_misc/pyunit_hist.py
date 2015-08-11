import sys
sys.path.insert(1, "../../")
import h2o

def hist_test(ip,port):
    
    

    kwargs = {}
    kwargs['server'] = True

    print "Import small prostate dataset"
    hex = h2o.import_frame(h2o.locate("smalldata/logreg/prostate.csv"))
    hex["AGE"].hist(**kwargs)
    hex["VOL"].hist(**kwargs)

if __name__ == "__main__":
    h2o.run_test(sys.argv, hist_test)
