df.describe()

# Rows: 100 Cols: 4
# 
# Chunk compression summary:
# chunk_type     chunkname   count   count_%   size   size_%
# ------------   ---------   -----   -------   ----   ------
# 64-bit Reals    C8D        4       100      3.4 KB  100
# 
# Frame distribution summary:
#                   size   #_rows   #_chunks_per_col  #_chunks
# ---------------  ------  ------   ---------------   --------
# 127.0.0.1:54321  3.4 KB  100      1                 4
# mean             3.4 KB  100      1                 4
# min              3.4 KB  100      1                 4
# max              3.4 KB  100      1                 4
# stddev           0  B    0        0                 0
# total            3.4 KB  100      1                 4
# 
#            A          B          C          D
# -------   --------   --------   --------   -------- 
# type      real       real       real       real
# mins      -2.49822   -2.37446   -2.45977   -3.48247
# mean      -0.01062   -0.23159    0.11423   -0.16228
# maxs       2.59380    1.91998    3.13014    2.39057
# sigma      1.04354    0.90576    0.96133    1.02608
# zeros      0          0          0          0
# missing    0          0          0          0