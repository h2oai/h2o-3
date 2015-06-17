

import h2o

h2o.init()


fr = h2o.import_frame("smalldata/logreg/prostate.csv")


print "fr[0] :"
a = fr[0]
a.visit()
a._pprint()


print "fr[:,1] :"
a = fr[:,1]
a.visit()
a._pprint()


print "fr[0] + 2 - fr[1] :"
a = fr[0] + 2 - fr[1]
a.visit()
a._pprint()


print "fr[fr[1] >= 2,1] :"
a = fr[fr[1] >= 2,1]
a.visit()
a._pprint()