# -*- encoding: utf-8 -*-
# mutable versions of py immutable types
from h2o.exceptions import H2OError

__no_export = set(dir())  # all variables defined above this are not exported

class _MObject(object): pass
class _MTuple(tuple): pass
class _MList(list): pass
class _MDict(dict): pass
class _MStr(str): pass


RAISE_ON_FIGURE_ACCESS = object()


def decorate_plot_result(res=None, figure=None):
    def get_figure():
        if figure is not RAISE_ON_FIGURE_ACCESS:
            return figure
        else:
            raise H2OError("Cannot plot, matplotlib is absent!")
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


def is_decorated_plot_result(obj):
    return hasattr(obj, "_is_decorated_plot_result")


__all__ = [s for s in dir() if not s.startswith('_') and s not in __no_export]
