import sys
sys.path.insert(1, "../../../")
import h2o

def multi_dim_slicing(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    prostate = h2o.import_frame(path=h2o.locate("smalldata/logreg/prostate.csv"))

    # prostate[int,int] case
    # 48,0,68,1,2,1,12.3,16.3,8
    pros = prostate[47:51,7]
    assert h2o.as_list(pros == 16.3)[0][0], "Incorrect slicing result"
    pros = prostate[172,8]
    assert h2o.as_list(pros == 7)[0][0], "Incorrect slicing result"

    # prostate[slice,int] case
    # rows:
    # 171,1,74,1,3,1,7,0,6
    # 172,1,71,1,3,1,3.3,0,6
    # 173,1,60,1,4,1,7.3,0,7
    # 174,1,62,1,2,1,17.2,0,7
    # 175,0,71,1,2,1,3.8,19,6
    # 176,0,67,1,3,1,5.7,15.4,6
    pros = prostate[170:176,2]
    assert h2o.as_list(pros[0,0] == 74)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[1,0] == 71)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[2,0] == 60)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[3,0] == 62)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[4,0] == 71)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[5,0] == 67)[0][0], "Incorrect slicing result"

    # # prostate[int,list] case
    # # 353,0,54,1,3,1,21.6,25,7
    # # 226,0,68,1,2,1,12.7,0,7
    # # 238,0,66,1,2,1,11,36.6,6
    # pros = prostate[6,[352,225,237]]
    # assert (pros[0,0] - 12.7) < 1e-10, "Incorrect slicing result"
    # assert (pros[0,1] - 11) < 1e-10, "Incorrect slicing result"
    # assert (pros[0,2] - 21.6) < 1e-10, "Incorrect slicing result"

    # prostate [int,slice] case
    # 189,1,69,1,3,2,8,31.2,6
    pros = prostate[188,0:3]
    assert h2o.as_list(pros[0,0] == 189)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[0,1] + 1 == 2)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[0,2] == 69)[0][0], "Incorrect slicing result"

    # prostate [slice,slice] case
    # 84,0,75,1,2,1,11,35,7
    # 85,0,75,1,1,1,9.9,15.4,7
    # 86,1,75,1,3,1,3.7,0,6
    pros = prostate[83:86,1:4]
    assert h2o.as_list(pros[0,0] == 0)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[0,1] == 75)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[0,2] - 1 == 0)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[1,0] == 0)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[1,1] + 75 == 150)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[1,2] == 1)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[2,0] + 1 == 2)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[2,1] == 75)[0][0], "Incorrect slicing result"
    assert h2o.as_list(pros[2,2] == 1)[0][0], "Incorrect slicing result"

    # # prostate [slice,list] case
    # # 27,0,67,1,2,1,2.8,25.6,7
    # # 9,0,69,1,1,1,3.9,24,7
    # # 201,0,57,1,1,1,10.2,0,6
    # pros = prostate[5:8,[26,8,200]]
    # assert pros[0,0] == 1, "Incorrect slicing result"
    # assert (pros[1,0]-3.9) < 1e-10, "Incorrect slicing result"
    # assert pros[2,0] == 24, "Incorrect slicing result"
    # assert pros[0,1] == 1, "Incorrect slicing result"
    # assert (pros[1,1]-2.8) < 1e-10, "Incorrect slicing result"
    # assert (pros[2,1]-25.6) < 1e-10, "Incorrect slicing result"
    # assert pros[0,2] == 1, "Incorrect slicing result"
    # assert (pros[1,2]-10.2) < 1e-10, "Incorrect slicing result"
    # assert pros[2,2] == 0, "Incorrect slicing result"
    #
    # # prostate [list,int] case
    # # 189,1,69,1,3,2,8,31.2,6
    # pros = prostate[[0,1,2],188]
    # assert pros[0,0] == 189, "Incorrect slicing result"
    # assert pros[1,0] == 1, "Incorrect slicing result"
    # assert pros[2,0] == 69, "Incorrect slicing result"
    #
    # # prostate [list,slice] case
    # # 84,0,75,1,2,1,11,35,7
    # # 85,0,75,1,1,1,9.9,15.4,7
    # # 86,1,75,1,3,1,3.7,0,6
    # pros = prostate[[1,2,3],83:86]
    # assert pros[0,0] == 0, "Incorrect slicing result"
    # assert pros[1,0] == 75, "Incorrect slicing result"
    # assert pros[2,0] == 1, "Incorrect slicing result"
    # assert pros[0,1] == 0, "Incorrect slicing result"
    # assert pros[1,1] == 75, "Incorrect slicing result"
    # assert pros[2,1] == 1, "Incorrect slicing result"
    # assert pros[0,2] == 1, "Incorrect slicing result"
    # assert pros[1,2] == 75, "Incorrect slicing result"
    # assert pros[2,2] == 1, "Incorrect slicing result"
    #
    # # prostate [list,list] case
    # # 27,0,67,1,2,1,2.8,25.6,7
    # # 9,0,69,1,1,1,3.9,24,7
    # # 201,0,57,1,1,1,10.2,0,6
    # pros = prostate[[5,6,7],[26,8,200]]
    # assert pros[0,0] == 1, "Incorrect slicing result"
    # assert (pros[1,0]-3.9) < 1e-10, "Incorrect slicing result"
    # assert pros[2,0] == 24, "Incorrect slicing result"
    # assert pros[0,1] == 1, "Incorrect slicing result"
    # assert (pros[1,1]-2.8) < 1e-10, "Incorrect slicing result"
    # assert (pros[2,1]-25.6) < 1e-10, "Incorrect slicing result"
    # assert pros[0,2] == 1, "Incorrect slicing result"
    # assert (pros[1,2]-10.2) < 1e-10, "Incorrect slicing result"
    # assert pros[2,2] == 0, "Incorrect slicing result"

if __name__ == "__main__":
    h2o.run_test(sys.argv, multi_dim_slicing)