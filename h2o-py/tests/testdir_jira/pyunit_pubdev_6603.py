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
    h2o.remove([hf, s1, s2])
    assert len(h2o.ls()) == 0
if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6603)
else:
    pubdev_6603()
