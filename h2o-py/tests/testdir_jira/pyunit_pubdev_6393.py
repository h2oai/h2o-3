#!/usr/bin/env python
# -*- coding: utf-8 -*- 
from h2o import H2OFrame
from tests import pyunit_utils


def pubdev_6393():
    locations = [['location'],
             ['�Ｘ県 Ａ市 '], # First observation contains replacement character for unknown char
             ['Ｘ県 Ｂ市']]

    frame = H2OFrame(locations, header=True, column_types=['enum'])
    assert frame.ncols == 1
    assert frame.nrows == len(locations) - 1
    
    frame_categories= frame['location'].categories()
    print(frame_categories)
    
    frame_converted = frame['location'].ascharacter().asfactor()
    assert frame_converted.ncols == 1
    assert frame_converted.nrows == len(locations) - 1
    
    frame_converted_categories = frame_converted.categories();
    print(frame_converted_categories)
    
    # Check for the representation of categoricals to be exactly the same
    # No explicit check for any specific behavior, the behavior of Categorical and asFactor should be the same
    for i in range(0,len(frame_converted_categories)):
        assert frame_categories[i] == frame_converted_categories[i]

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6393)
else:
    pubdev_6393()
