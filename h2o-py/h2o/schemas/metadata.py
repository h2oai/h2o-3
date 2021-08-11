# -*- encoding: utf-8 -*-
#
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
from __future__ import division, print_function, absolute_import, unicode_literals
# noinspection PyUnresolvedReferences
from h2o.utils.compatibility import *  # NOQA


class H2OMetadataV3(object):

    @classmethod
    def make(cls, json_kv_pairs):
        return cls(json_kv_pairs)

    def __init__(self, json_kv_pairs):
        self._schema = next((v for k, v in json_kv_pairs if k == 'schemas'), [None])[0]
        assert self._schema
        
    @property
    def name(self):
        return self._schema.get('name')

    @property
    def fields(self):
        return [_Field(f) for f in self._schema.get('fields')]
    
    def __repr__(self):
        return repr({k: getattr(self, k) for k in dir(self) if not k.startswith('_')})
    
    
class _Field(object):
    
    def __init__(self, j_field):
        self._field = j_field
    
    @property
    def name(self):
        return self._field.get('name')
    
    @property
    def is_schema(self):
        return self._field.get('is_schema')
    
    @property
    def help(self):
        return self._field.get('help')
    
    def __repr__(self):
        return repr({k: getattr(self, k) for k in dir(self) if not k.startswith('_')})
