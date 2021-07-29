import sys
sys.path.insert(1,"../../")
import h2o
import math
import os
from tests import pyunit_utils


def pubdev_6339():
    
    cluster = h2o.cluster()
    # number of nodes
    cloud_size = cluster.cloud_size
    # number of CPUs
    cores = sum(node["num_cpus"] for node in cluster.nodes)


    # path to file
    file_paths = [
        pyunit_utils.locate("smalldata/arcene/arcene_train.data"),
        pyunit_utils.locate("smalldata/census_income/adult_data.csv"),
        pyunit_utils.locate("smalldata/chicago/chicagoAllWeather.csv"),
        pyunit_utils.locate("smalldata/gbm_test/alphabet_cattest.csv"),
        pyunit_utils.locate("smalldata/wa_cannabis/raw/Dashboard_Usable_Sales_w_Weight_Daily.csv")
    ]

    for file_path in file_paths:
        # read data and parse setup to get number of columns 
        data_raw = h2o.import_file(path=file_path,parse=False)
        setup = h2o.parse_setup(data_raw)

        # get number of columns from setup
        num_cols = setup['number_columns']
        # get the chunk size
        chunk_size = calculate_chunk_size(file_path, num_cols, cores, cloud_size)
    
        # get chunk size to compare if calculation is correct
        result_size = setup['chunk_size']
        assert chunk_size == result_size, "Calculated chunk size is incorrect!"
        print("chunk size for file", file_path, "is:", chunk_size)

    data_raw = h2o.import_file(path=file_paths[1],parse=False)
    setup = h2o.parse_setup(data_raw)
        
    
def calculate_chunk_size(file_path, num_cols, cores, cloud_size):
    """
        Return size of a chunk calculated for optimal data handling in h2o java backend.
    
        :param file_path:  path to dataset
        :param num_cols:  number or columns in dataset
        :param cores:  number of CPUs on machine where the model was trained
        :param cloud_size:  number of nodes on machine where the model was trained
        :return:  a chunk size 
    """
    
    # get maximal line size from file in bytes
    max_line_length = 0
    total_size = 0
    with open(file_path, "rU") as input_file:
        for line in input_file:
            size = len(line)
            total_size = total_size + size
            if size > max_line_length:
                max_line_length = size
    default_log2_chunk_size = 20+2
    default_chunk_size = 1 << default_log2_chunk_size
    local_parse_size = int(total_size / cloud_size)
    min_number_rows = 10  # need at least 10 rows (lines) per chunk (core)
    per_node_chunk_count_limit = 1 << 21  # don't create more than 2M Chunk POJOs per node
    min_parse_chunk_size = 1 << 12  # don't read less than this many bytes
    max_parse_chunk_size = (1 << 28)-1  # don't read more than this many bytes per map() thread 
    chunk_size = int(max((local_parse_size / (4*cores))+1, min_parse_chunk_size))  # lower hard limit
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
                # this times too many chunks globally on the cluster
                ratio = 1 << max(2, int(math.log(int(chunk_count / per_node_chunk_count_limit), 2)))  
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
