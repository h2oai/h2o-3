# -*- encoding: utf-8 -*-
# Copyright: (c) 2019 H2O.ai
# License:   Apache License Version 2.0 (see LICENSE for details)
"""
:mod:`base` -- base/abstract H2O classes used internally.
"""


class Keyed(object):

    @property
    def key(self):
        """
        :return: the unique key representing the object on the backend
        """
        raise NotImplementedError("Keyed classes should have a `key` property.")

    def detach(self):
        """
        Detach the Python object from the backend, usually by clearing its key
        """
        raise NotImplementedError("Keyed classes should implement `detach`.")
