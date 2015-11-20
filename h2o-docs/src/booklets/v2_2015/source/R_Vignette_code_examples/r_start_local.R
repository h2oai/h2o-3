library(h2o)
# Starts H2O using localhost IP, port 54321, all CPUs, and 4g of memory
h2o.init(ip = 'localhost', port = 54321, nthreads= -1, max_mem_size = '4g')