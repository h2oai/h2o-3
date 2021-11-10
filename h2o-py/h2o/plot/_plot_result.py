# -*- encoding: utf-8 -*-
# mutable versions of py immutable types
class _MObject(object): pass
class _MTuple(tuple): pass
class _MList(list): pass
class _MDict(dict): pass
class _MStr(str): pass
class Error(EnvironmentError): pass

def decorate_plot_result(res=None, figure=None):
    def get_figure():
        if figure != "RAISE_EXCEPTION_FLAG":
            return figure
        else:
            raise Error("Cannot plot, matplotlib is absent!")
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
    dec.figure = get_figure
    dec._is_decorated_plot_result = True
     
    return dec


