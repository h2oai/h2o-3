#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import division, print_function
import sys

sys.path.insert(1, "../../")

from h2o.display import H2ODisplay, H2ODisplayWrapper, H2OStringDisplay, H2OItemsDisplay
from tests import pyunit_utils as pu


_disp_constant = H2OStringDisplay("never changes")
_disp_spamegg = H2ODisplayWrapper(lambda fmt=None: dict(
    html="<spam>and eggs</spam>",
    pretty="No spam, just eggs please"
).get(fmt, "spam & eggs"))


def test_string_representations_of_constant_object():
    sd = _disp_constant
    assert repr(sd) == str(sd) == sd.to_str() == sd.to_pretty_str() == sd.to_html() == "never changes"


def test_string_representations_of_simple_object():
    obj = _disp_spamegg
    assert repr(obj).startswith("H2ODisplayWrapper")
    assert str(obj) == obj.to_str() == "spam & eggs"
    assert obj.to_pretty_str() == "No spam, just eggs please"
    assert obj.to_html() == "<spam>and eggs</spam>"
        

def test_items_string_representations_of_complex_object():
    items = H2OItemsDisplay([
        "first line",
        "second line",
        _disp_constant,
        "a line in between",
        "another line in between",
        _disp_spamegg,
        _disp_spamegg,
        "last line"
    ])
    assert str(items) == items.to_str() == """
first line
second line

never changes

a line in between
another line in between

spam & eggs

spam & eggs

last line
""".strip()  # notice the new lines around non raw strings, creating paragraphs
    
    assert items.to_pretty_str() == """
first line
second line

never changes

a line in between
another line in between

No spam, just eggs please

No spam, just eggs please

last line
""".strip()
    
    assert items.to_html() == """
<pre style='margin: 1em 0 1em 0;'>first line
second line</pre>
<div style='margin: 1em 0 1em 0;'>never changes</div>
<pre style='margin: 1em 0 1em 0;'>a line in between
another line in between</pre>
<div style='margin: 1em 0 1em 0;'><spam>and eggs</spam></div>
<div style='margin: 1em 0 1em 0;'><spam>and eggs</spam></div>
<pre style='margin: 1em 0 1em 0;'>last line</pre>
""".strip()


pu.run_tests([
    test_string_representations_of_constant_object,
    test_string_representations_of_simple_object,
    test_items_string_representations_of_complex_object,
])
