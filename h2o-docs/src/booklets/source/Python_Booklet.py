#---------------------------------------------------------------------
#
# Include and run all the Python code snippets from the H2O Python booklet.
#
# The snippets are broken out into separate files so the exact same
# piece of code both shows up in the document and is run by this
# script.
#
#---------------------------------------------------------------------


execfile("python/start_h2o.py", echo = T)

execfile("python/h2oframe_from_tuple.py", echo = T)

execfile("python/h2oframe_from_list.py", echo = T)

execfile("python/h2oframe_from_dict.py", echo = T)
