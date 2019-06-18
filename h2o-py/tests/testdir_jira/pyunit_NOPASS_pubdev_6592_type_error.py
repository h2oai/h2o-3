#!/usr/bin/env python
# -*- coding: utf-8 -*-


from tests import pyunit_utils

# NOPASS, after fixing a bug it throws a correct error.
# Note: the try-except interrupts another error behind this error, so it can´t be used in this test.
def test_type_error():
    from datatable import f
    import h2o
    h2o.init(0)
    
        
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_type_error)
else:
    test_type_error()
