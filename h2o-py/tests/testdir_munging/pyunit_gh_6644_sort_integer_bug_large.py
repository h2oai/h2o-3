import sys
import h2o
sys.path.insert(1,"../../")
from tests import pyunit_utils

def test_second_sort_bug():
    # from user.  The integer sort is buggy form some reason.
    data_frame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/sort_merge_tests/gh_6644_sort_bug_shorter.zstd"))
    sorted_frame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/sort_merge_tests/gh_6644_after_1st_sort_shorter.zstd"))
    # check and make sure integer sort is correct
    row_per_subframe = 500000
    nrow = data_frame.nrow
    resorted_frame = sorted_frame.sort([1])
    offset = 0
    num_for_loop = int(nrow/row_per_subframe)
    compare_result = []
    for ind in range(1, num_for_loop):
        low_index = (ind-1)*row_per_subframe
        high_index = ind*row_per_subframe
        print("checking row {0} : {1}".format(low_index, high_index))
        sub_frame1 = data_frame[low_index:high_index,1]
        sub_frame2 = resorted_frame[low_index:high_index, 1]
        compare_result_temp = pyunit_utils.compare_frame_local_one_int_column(sub_frame1, sub_frame2, offset)
        if len(compare_result_temp)>0:
            print(compare_result_temp[0])
            assert len(compare_result_temp)==0, "Integer sort failed at {0} rows".format(len(compare_result_temp))
            compare_result.extend(compare_result_temp)
            
        offset += row_per_subframe
    # work with remaining rows
    #        print("checking row {0} : {1}".format(offset, nrow-1))
    sub_frame1 = data_frame[offset:nrow, 1]
    sub_frame2 = resorted_frame[offset:nrow, 1]
    compare_result_temp = pyunit_utils.compare_frame_local_one_int_column(sub_frame1, sub_frame2, offset)
    if len(compare_result_temp) > 0:
        # with open("/Users/wendycwong/temp/badSort.txt", "w") as f:
        #     for s in compare_result:
        #         f.write(str(s)+"\n")
        print("Lenght of bad sort: {0}".format(len(compare_result_temp)))
        assert len(compare_result_temp)==0, "Integer sort with {0} rows".format(len(compare_result_temp))
    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_second_sort_bug)
else:
    test_second_sort_bug()
