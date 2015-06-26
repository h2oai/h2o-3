import sys
sys.path.insert(1, "../../../")
import h2o

def frame_slicing(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    prostate = h2o.import_frame(path=h2o.locate("smalldata/prostate/prostate.csv.zip"))
    airlines = h2o.import_frame(path=h2o.locate("smalldata/airlines/allyears2k.zip"))
    iris.show()
    prostate.show()
    airlines.show()

    ###################################################################

    # H2OFrame[int] (column slice)
    res1 = iris[0]
    assert abs(res1[8,:] - 4.4) < 1e-10, "incorrect values"

    # H2OFrame[int,int]
    res2 = prostate[13, 3]
    assert abs(res2 - 1) < 1e-10, "incorrect values"

    # H2OFrame[int, slice]
    res3 = airlines[12, 0:3]
    assert abs(res3[0,0] - 1987) < 1e-10 and abs(res3[0,1] - 10) < 1e-10 and abs(res3[0,2] - 29) < 1e-10, \
        "incorrect values"

    # H2OFrame[slice, int]
    res4 = iris[5:8, 1]
    assert abs(res4[0,:] - 3.9) < 1e-10 and abs(res4[1,:] - 3.4) < 1e-10 and abs(res4[2,:] - 3.4) < 1e-10, "incorrect values"

    # H2OFrame[slice, slice]
    res5 = prostate[5:8, 0:3]
    assert abs(res5[0,0] - 6) < 1e-10 and abs(res5[1,1] - 0) < 1e-10 and abs(res5[2,2] - 61) < 1e-10, "incorrect values"

if __name__ == "__main__":
    h2o.run_test(sys.argv, frame_slicing)
