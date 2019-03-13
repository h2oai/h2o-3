import sys
sys.path.insert(1,"../../")
import h2o
import math
from tests import pyunit_utils


def pubdev_6339():
    data_raw = h2o.import_file(path="hdfs://mr-0xd6.0xdata.loc:8020/user/megan/reproducibility_train.csv",parse=False)
    setup = h2o.parse_setup(data_raw)
    data = h2o.parse_raw(setup)
    desc = data.describe(chunk_summary=True)

    # get from user user
    cores = 8
    cloud_size = 1
    
    # total size of data in bytes before compression
    # impossible to get this information from python API
    total_size = 390333275
    # calculated based on number of columns, column type and number of NA's, 
    # impossible to get this information from python API, hard to calculate in Python
    maxLineLength = 1164
    
    # get from setup
    num_cols = setup['number_columns']
    result_size = setup['chunk_size']
    
    chunk_size = calc_optimal_chunk_size(total_size, num_cols, cores, cloud_size, maxLineLength)
   
    print("chunk size:", chunk_size)
    print("result_size:", result_size)
    assert chunk_size == result_size
    

def calc_optimal_chunk_size(total_size, num_cols, cores, cloud_size, max_line_length):
    default_log2_chunk_size = 20+2
    default_chunk_size = 1 << default_log2_chunk_size
    local_parse_size = int(total_size / cloud_size)
    min_number_rows = 10  # need at least 10 rows (lines) per chunk (core)
    per_node_chunk_count_limit = 1 << 21  # don't create more than 2M Chunk POJOs per node
    min_parse_chunk_size = 1 << 12  # don't read less than this many bytes
    max_parse_chunk_size = (1 << 28)-1  # don't read more than this many bytes per map() thread (needs to fit into a Value object)
    chunk_size = int(max((local_parse_size / (4*cores))+1, min_parse_chunk_size))#lower hard limit
    if chunk_size > 1024*1024:
        chunk_size = (chunk_size & 0xFFFFFE00) + 512  # align chunk size to 512B
        # Super small data check - file size is smaller than 64kB
    if total_size <= 1 << 16:
        chunk_size = max(default_chunk_size, int(min_number_rows * max_line_length))
    else:
        # Small data check
        if (chunk_size < default_chunk_size) and ((local_parse_size / chunk_size) * num_cols < per_node_chunk_count_limit):
            chunk_size = max(int(chunk_size), int(min_number_rows * max_line_length))
        else:
            # Adjust chunk_size such that we don't create too many chunks
            chunk_count = cores * 4 * num_cols
            if chunk_count > per_node_chunk_count_limit:
                # convert chunk count to long double
                ratio = 1 << max(2, int(math.log2(int(chunk_count / per_node_chunk_count_limit))))  # this times too many chunks globally on the cluster
                chunk_size = chunk_size * ratio  # need to bite off larger chunks
            chunk_size = min(max_parse_chunk_size, chunk_size)  # hard upper limit
            # if we can read at least min_number_rows and we don't create too large Chunk POJOs, we're done
            # else, fix it with a catch-all heuristic
            if chunk_size <= min_number_rows * max_line_length:
                # might be more than default, if the max line length needs it, but no more than the size limit(s)
                # also, don't ever create too large chunks
                chunk_size = int(max(default_chunk_size,  # default chunk size is a good lower limit for big data
                    min(max_parse_chunk_size, min_number_rows * max_line_length)))  # don't read more than 1GB, but enough to read the minimum number of rows)
    return int(chunk_size) 
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6339)
else:
    pubdev_6339()
