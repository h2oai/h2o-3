#!/usr/bin/env python
# -*- coding: utf-8 -*- 
import sys
sys.path.insert(1,"../../")
from tests import pyunit_utils
import h2o
import pandas as pd
 

def pubdev_6603():
    hf = h2o.H2OFrame(pd.DataFrame([{'a': 1, 'b': 2}, {'a': 3, 'b': 4}]))
    s1, s2 = hf.split_frame(ratios=[0.5], seed=1)
    frame_ids = [s1.frame_id, s2.frame_id, hf.frame_id]
    h2o.remove([hf, s1, s2])
    
    for id in frame_ids:
        assert h2o.get_frame(id) is None
if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6603)
else:
    pubdev_6603()
