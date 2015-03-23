#----------------------------------------------------------------------
# Try to slice by using != factor_level
#----------------------------------------------------------------------

import sys
sys.path.insert(1, "../../")
import h2o

def not_equal_factor(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    air = h2o.import_frame(path=h2o.locate("smalldata/airlines/allyears2k_headers.zip"))

    # Print dataset size.
    rows, cols = air.dim()

    #
    # Example 1: Select all flights not departing from SFO
    #

    not_sfo = air[air["Origin"] != "SFO"]
    sfo = air[air["Origin"] == "SFO"]
    no_rows, no_cols = not_sfo.dim()
    yes_rows, yes_cols = sfo.dim()
    assert (no_rows + yes_rows) == rows and no_cols == yes_cols == cols, "dimension mismatch"

if __name__ == "__main__":
    h2o.run_test(sys.argv, not_equal_factor)
