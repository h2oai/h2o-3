#!/usr/bin/env python
# -*- coding: utf-8 -*- 
from h2o import H2OFrame
from tests import pyunit_utils
import sys


def pubdev_6439():
    data = [['C1'],
             ['Ｘ県 Ａ市 '], # First observation contains replacement character for unknown char
             ['Ｘ県 Ｂ市']]

    frame = H2OFrame(data, header=True, column_types=['enum'])
    
    frame_categories= frame['C1'].categories()
    print(frame_categories)
    
    # Two observations
    assert len(frame_categories) == 2
    assert len(frame_categories[0]) == 6 # First observation has six characters (space at the end)
    assert len(frame_categories[1]) == 5 # Second observation has 5 characters (missing space at the end)
    
    # Python 2 and 3 handle strings differently
    if(sys.version_info[0] == 3):
        assert ''.join(data[1]) == frame_categories[0] # First categorical level equals to first observation
        assert ''.join(data[2]) == frame_categories[1] # Second categorical levels equals to second observation
    elif(sys.version_info[0] == 2):
        assert ''.join(data[1]).decode("utf-8") == frame_categories[0] # First categorical level equals to first observation
        assert ''.join(data[2]).decode("utf-8") == frame_categories[1] # Second categorical levels equals to second observation
    else:
        assert False
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6439)
else:
    pubdev_6439()
