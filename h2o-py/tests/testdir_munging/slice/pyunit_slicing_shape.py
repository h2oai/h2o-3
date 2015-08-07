# Check that slicings give the correct shape
import sys
sys.path.insert(1, "../../../")
import h2o
import random

def slicing_shape(ip,port):
    # Connect to a pre-existing cluster
    

    prostate = h2o.import_frame(path=h2o.locate("smalldata/logreg/prostate.csv"))
    rows, cols = prostate.dim()

    #foo = prostate[0:0] # TODO: empty frame allowed?
    #foo.show()

    # prostate[slice]
    for ncols in range(1,cols+1):
        r, c = prostate[0:ncols].dim()
        assert r == rows, "incorrect number of rows. correct: {0}, computed: {1}".format(rows, r)
        assert c == ncols, "incorrect number of cols. correct: {0}, computed: {1}".format(ncols, c)

    # prostate[int,slice]
    for ncols in range(1,cols+1):
        r, c = prostate[random.randint(0,rows-1),0:ncols].dim()
        assert r == 1, "incorrect number of rows. correct: {0}, computed: {1}".format(1, r)
        assert c == ncols, "incorrect number of cols. correct: {0}, computed: {1}".format(ncols, c)

    # prostate[slice,int]
    for nrows in range(1,10):
       r, c = prostate[0:nrows,random.randint(0,cols-1)].dim()
       assert r == nrows, "incorrect number of rows. correct: {0}, computed: {1}".format(nrows, r)
       assert c == 1, "incorrect number of cols. correct: {0}, computed: {1}".format(1, c)

    # prostate[slice,slice]
    for nrows in range(1,10):
       for ncols in range(1,cols+1):
           r, c = prostate[0:nrows,0:ncols].dim()
           assert r == nrows, "incorrect number of rows. correct: {0}, computed: {1}".format(nrows, r)
           assert c == ncols, "incorrect number of cols. correct: {0}, computed: {1}".format(ncols, c)

if __name__ == "__main__":
    h2o.run_test(sys.argv, slicing_shape)
