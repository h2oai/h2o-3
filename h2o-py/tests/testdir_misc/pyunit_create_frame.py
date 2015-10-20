


import random

def create_frame_test():
    
    

    # REALLY basic test TODO: add more checks
    r = random.randint(1,1000)
    c = random.randint(1,1000)

    frame = h2o.create_frame(rows=r, cols=c)
    assert frame.nrow == r and frame.ncol == c, "Expected {0} rows and {1} cols, but got {2} rows and {3} " \
                                                    "cols.".format(r,c,frame.nrow,frame.ncol)


create_frame_test()
