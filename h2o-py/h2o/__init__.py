# -*- encoding: utf-8 -*-
# Copyright: (c) 2016 H2O.ai
# License:   Apache License Version 2.0 (see LICENSE for details)
"""
:mod:`h2o` -- module for using H2O services.

"""
from __future__ import absolute_import, division, print_function, unicode_literals

from codecs import open
import os
import sys
import zipfile

__no_export = set(dir())  # variables defined above this are not exported

from h2o.h2o import (connect, init, api, connection, resume,
                     lazy_import, upload_file, import_file, import_sql_table, import_sql_select, import_hive_table,
                     parse_setup, parse_raw, assign, deep_copy, models, get_model, get_grid, get_frame,
                     show_progress, no_progress, enable_expr_optimizations, is_expr_optimizations_enabled,
                     log_and_echo, remove, remove_all, rapids,
                     ls, frame, frames, create_frame, load_frame,
                     download_pojo, download_csv, download_all_logs, save_model, download_model, upload_model, load_model,
                     export_file,
                     cluster_status, cluster_info, shutdown, network_test, cluster,
                     interaction, as_list,
                     get_timezone, set_timezone, list_timezones,
                     load_dataset, demo, make_metrics, flow, upload_custom_metric, upload_custom_distribution,
                     import_mojo, upload_mojo, print_mojo, load_grid, save_grid, estimate_cluster_mem)
from h2o.utils.shared_utils import mojo_predict_csv, mojo_predict_pandas
from h2o.scoring import make_leaderboard
from h2o.frame import H2OFrame  # NOQA
# We have substantial amount of code relying on h2o.H2OFrame to exist. Thus, we make this class available from
# root h2o module, without exporting it explicitly. In the future this import may be removed entirely, so that
# one would have to import it from h2o.frames.
__no_export.add('H2OFrame')

_here = os.path.abspath(os.path.dirname(__file__))


def _read_txt_from_whl(name, fallback):
    if _here.endswith('.whl/h2o'):
        with zipfile.ZipFile(_here[:-4]) as whl:
            with whl.open(name) as f:
                return f.read().decode('utf8')
    else:
        return fallback


try:
    with open(os.path.join(_here, 'buildinfo.txt'), encoding='utf-8') as f:
        __buildinfo__ = f.read()
except:
    try:
        __buildinfo__ = _read_txt_from_whl('h2o/buildinfo.txt', "unknown")
    except:
        __buildinfo__ = "unknown"

try:
    with open(os.path.join(_here, 'version.txt'), encoding='utf-8') as f:
        __version__ = f.read()
except:
    try:
        __version__ = _read_txt_from_whl('h2o/version.txt', "0.0.local")
    except:
        __version__ = "0.0.local"

if __version__.endswith("99999"):
    print(__buildinfo__)


try:
    # Export explain functions that are useful for lists of models
    from h2o.explanation import register_explain_methods as _register_explain_methods
    from h2o.explanation import explain, explain_row, varimp_heatmap, model_correlation_heatmap, pd_multi_plot

    _register_explain_methods()
except ImportError:
    pass


__all__ = [s for s in dir()
           if not s.startswith('_') 
           and s not in __no_export 
           and "h2o.{}".format(s) not in sys.modules]


def _init_():
    from .display import ReplHook, in_py_repl
    from .backend.connection import register_session_hook
    if in_py_repl():
        replhook = ReplHook()
        register_session_hook('open', replhook.__enter__)
        register_session_hook('close', replhook.__exit__)
    
    
_init_()
