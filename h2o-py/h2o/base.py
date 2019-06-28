# -*- encoding: utf-8 -*-
# Copyright: (c) 2019 H2O.ai
# License:   Apache License Version 2.0 (see LICENSE for details)
"""
:mod:`abstract` -- abstract H2O classes used internally.
"""


class Keyed(object):

    @property
    def id(self):
        raise NotImplementedError
