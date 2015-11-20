import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def vec_scaler_comparisons():
    # Connect to a pre-existing cluster


    air = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"))
    rows, cols = air.dim

    ## H2OVec/scaler
    # ==
    row_sum = 0
    levels = air[16].levels()
    for level in levels[0]:
       r, c = air[air["Origin"] == str(level)].dim
       row_sum += r
    assert row_sum == rows, "expected equal number of rows"

    # ==, !=
    jan = air[air["Month"] == 1]
    not_jan = air[air["Month"] != 1]
    no_rows, no_cols = not_jan.dim
    yes_rows, yes_cols = jan.dim
    assert (no_rows + yes_rows) == rows and no_cols == yes_cols == cols, "expected equal number of rows and cols"

    # >, <=
    g = air[air["Year"] > 1990]
    L = air[air["Year"] <= 1990]
    g_rows, g_cols = g.dim
    L_rows, L_cols = L.dim
    assert (L_rows + g_rows) == rows and L_cols == g_cols == cols, "expected equal number of rows and cols"

    # >=, <
    G = air[air["DayofMonth"] >= 15]
    l = air[air["DayofMonth"] < 15]
    G_rows, G_cols = G.dim
    l_rows, l_cols = l.dim
    assert (l_rows + G_rows) == rows and l_cols == G_cols == cols, "expected equal number of rows and cols"

    ## scaler/H2OVec
    # ==
    row_sum = 0
    for level in levels[0]:
       r, c = air[str(level) == air["Origin"]].dim
       row_sum += r
    assert row_sum == rows, "expected equal number of rows"

    # ==, !=
    jan = air[1 == air["Month"]]
    not_jan = air[1 != air["Month"]]
    no_rows, no_cols = not_jan.dim
    yes_rows, yes_cols = jan.dim
    assert (no_rows + yes_rows) == rows and no_cols == yes_cols == cols, "expected equal number of rows and cols"

    # >, <=
    g = air[1990 <= air["Year"]]
    L = air[1990 > air["Year"]]
    g_rows, g_cols = g.dim
    L_rows, L_cols = L.dim
    assert (L_rows + g_rows) == rows and L_cols == g_cols == cols, "expected equal number of rows and cols"

    # >=, <
    G = air[15 < air["DayofMonth"]]
    l = air[15 >= air["DayofMonth"]]
    G_rows, G_cols = G.dim
    l_rows, l_cols = l.dim
    assert (l_rows + G_rows) == rows and l_cols == G_cols == cols, "expected equal number of rows and cols"



if __name__ == "__main__":
    pyunit_utils.standalone_test(vec_scaler_comparisons)
else:
    vec_scaler_comparisons()
