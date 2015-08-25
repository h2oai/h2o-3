import sys
sys.path.insert(1, "../../")
import h2o, tests

def runif_check(ip, port):
    # Connect to a pre-existing cluster
    

    uploaded_frame = h2o.upload_file(h2o.locate("bigdata/laptop/mnist/train.csv.gz"))
    r_u = uploaded_frame[0].runif(1234)

    imported_frame = h2o.import_file(h2o.locate("bigdata/laptop/mnist/train.csv.gz"))
    r_i = imported_frame[0].runif(1234)

    print "This demonstrates that seeding runif on identical frames with different chunk distributions provides " \
          "different results. upload_file: {0}, import_frame: {1}.".format(r_u.mean(), r_i.mean())

if __name__ == "__main__":
    tests.run_test(sys.argv, runif_check)
