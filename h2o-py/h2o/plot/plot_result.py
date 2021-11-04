# -*- encoding: utf-8 -*-
# mutable versions of py immutable types
class _MObject(object): pass
class _MTuple(tuple): pass
class _MList(list): pass
class _MDict(dict): pass
class _MStr(str): pass

def decorate_plot_result(res, figure=None):
    # list all special/immutable types that we need first
    if res is None:
        dec = _MObject()
    elif isinstance(res, tuple):
        dec = _MTuple(res)
    elif isinstance(res, list):
        dec = _MList(res)
    elif isinstance(res, dict):
        dec = _MDict(res)
    elif isinstance(res, str):
        dec = _MStr(res)
    else: # should be an H2O instance, should be mutable
        dec = res
    dec.figure = figure
    return dec


