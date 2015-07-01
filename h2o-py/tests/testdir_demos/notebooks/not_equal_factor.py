#----------------------------------------------------------------------
# Try to slice by using != factor_level
#----------------------------------------------------------------------
import h2o
air = h2o.import_frame(path=h2o.locate("smalldata/airlines/allyears2k_headers.zip"))
rows, cols = air.dim()
print [rows, cols]
#
# Select all flights not departing from SFO
#
not_sfo = air[air["Origin"] != "SFO"]
sfo = air[air["Origin"] == "SFO"]
no_rows, no_cols = not_sfo.dim()
yes_rows, yes_cols = sfo.dim()
print "no_rows: {0}".format(no_rows)
print "yes_rows: {0}".format(yes_rows)
print "no_cols: {0}".format(no_cols)
print "yes_cols: {0}".format(yes_cols)
