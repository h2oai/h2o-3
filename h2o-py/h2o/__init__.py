# encoding: utf-8
# module h2o
# from (h2o)
from __future__ import absolute_import
__version__ = "SUBST_PROJECT_VERSION"

from .h2o import *

__all__ = ['assembly', 'astfun', 'connection', 'cross_validation', 'display',
           'expr', 'frame', 'group_by', 'h2o',
           'job', 'two_dim_table', 'estimators', 'grid', 'model', 'transforms']

def data_file(relative_path):
    """Return absolute path to a file within the 'h2o' folder."""
    h2o_dir = os.path.split(__file__)[0]
    return os.path.join(h2o_dir, relative_path)
