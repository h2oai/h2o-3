import sys
sys.path.insert(1, "../../../")
import h2o

def multi_dim_slicing(ip,port):
    # Connect to a pre-existing cluster
    

    prostate = h2o.import_frame(path=h2o.locate("smalldata/logreg/prostate.csv"))

    # prostate[int,int] case
    # 48,0,68,1,2,1,12.3,16.3,8
    pros = prostate[47:51,7]
    assert pros[0,0] == 16.3, "Incorrect slicing result"
    pros = prostate[172,8]
    assert pros == 7, "Incorrect slicing result"

    # prostate[slice,int] case
    # rows:
    # 171,1,74,1,3,1,7,0,6
    # 172,1,71,1,3,1,3.3,0,6
    # 173,1,60,1,4,1,7.3,0,7
    # 174,1,62,1,2,1,17.2,0,7
    # 175,0,71,1,2,1,3.8,19,6
    # 176,0,67,1,3,1,5.7,15.4,6
    pros = prostate[170:176,2]
    assert pros[0,0] == 74, "Incorrect slicing result"
    assert pros[1,0] == 71, "Incorrect slicing result"
    assert pros[2,0] == 60, "Incorrect slicing result"
    assert pros[3,0] == 62, "Incorrect slicing result"
    assert pros[4,0] == 71, "Incorrect slicing result"
    assert pros[5,0] == 67, "Incorrect slicing result"

    # prostate [int,slice] case
    # 189,1,69,1,3,2,8,31.2,6
    pros = prostate[188,0:3]
    assert pros[0,0] == 189, "Incorrect slicing result"
    assert pros[0,1] + 1 == 2, "Incorrect slicing result"
    assert pros[0,2] == 69, "Incorrect slicing result"

    # prostate [slice,slice] case
    # 84,0,75,1,2,1,11,35,7
    # 85,0,75,1,1,1,9.9,15.4,7
    # 86,1,75,1,3,1,3.7,0,6
    pros = prostate[83:86,1:4]
    assert pros[0,0] == 0, "Incorrect slicing result"
    assert pros[0,1] == 75, "Incorrect slicing result"
    assert pros[0,2] - 1 == 0, "Incorrect slicing result"
    assert pros[1,0] == 0, "Incorrect slicing result"
    assert pros[1,1] + 75 == 150, "Incorrect slicing result"
    assert pros[1,2] == 1, "Incorrect slicing result"
    assert pros[2,0] + 1 == 2, "Incorrect slicing result"
    assert pros[2,1] == 75, "Incorrect slicing result"
    assert pros[2,2] == 1, "Incorrect slicing result"

if __name__ == "__main__":
    h2o.run_test(sys.argv, multi_dim_slicing)
