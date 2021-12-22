#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Pyunit for h2o.utils.progressbar."""
from __future__ import absolute_import, division, print_function

try:
    from StringIO import StringIO  # py2 (first as py2 also has io.StringIO, but without string support, only unicode)
except:
    from io import StringIO   # py3
    
import math
import random
import time

from h2o.utils.progressbar import ProgressBar, ProgressBarWidget, PBWBar, PBWPercentage, PBWString, RenderResult


def progress_generator(duration):
    interval = duration / 20
    for i in range(20):
        yield (i + 1) / 20, interval


def progress_fast():
    yield 0, 0.2
    for i in range(10):
        yield 0.9 + (i // 5) * 0.03, 0.2
    while True:
        yield 1, 0


def super_fast(start=0):
    yield start, 0.1
    yield 1, 0.1


def not_so_fast(end=0.99):
    yield 0
    for i in range(10):
        yield i / 10
    for i in range(10):
        if i == 2: time.sleep(0.5)
        yield 0.99
    yield 1


def random_progress_generator(duration, interrupted=False):
    progress = 0
    n_steps = 10
    last_t = time.time()
    beta = n_steps / duration
    while progress < n_steps:
        delta = time.time() - last_t
        last_t = time.time()
        if interrupted and random.random() + progress / n_steps > math.exp(-beta * delta / (n_steps / 4)):
            return  # we can't specify any return value (converted into StopIteration error message) as Py2.7 doesn't support return value in generators.
        if random.random() > math.exp(-beta * delta):
            progress += 1
        yield progress / n_steps


def pybooklet_progress():
    last_time = 0
    for t, x in [(0.0, 0), (0.2, 0), (0.4, 0.316), (0.6, 0.316), (0.8, 0.316), (1.0, 0.316), (1.3, 0.316),
                 (1.5, 0.316), (1.8, 0.316), (2.2, 0.316), (2.7, 0.316), (3.2, 0.316), (3.9, 0.631),
                 (4.5, 0.631), (5.2, 0.631), (5.9, 0.947), (6.4, 0.947), (6.9, 0.990), (7.5, 0.990),
                 (8.0, 0.990), (8.6, 0.990), (9.1, 0.990), (10.9, 0.990), (11.5, 0.990), (12, 1.000)]:
        yield x, t - last_time
        last_time = t
    yield 1
        
        
def test_progressbar():
    """Test functionality for the progress bar."""
    ProgressBar().execute(progress_generator(4))
    ProgressBar("With file_mode", file_mode=True).execute(progress_generator(4))
    ProgressBar(widgets=[PBWString("Clowncopterization in progress, stand WAY back!"), PBWBar(), PBWPercentage()])\
        .execute(progress_generator(3))

    g = progress_fast()
    ProgressBar().execute(g)
    curr_progress = next(g)[0]
    assert curr_progress == 1, "Progress bar finished but the progress is %f" % curr_progress

    ProgressBar("Super-fast").execute(super_fast())
    ProgressBar("Super-duper-fast").execute(super_fast(0.3))
    ProgressBar("Super-duper-mega-fast").execute(super_fast(0.9))
    ProgressBar("Lightning").execute(lambda: 1)
    ProgressBar("Not so fast...").execute(not_so_fast())

    ProgressBar("Random 1s").execute(random_progress_generator(1))
    ProgressBar("Random 5s").execute(random_progress_generator(5))
    ProgressBar("Random 10s").execute(random_progress_generator(10))
    ProgressBar("Hope this one works").execute(random_progress_generator(5, True))

    gen = pybooklet_progress()
    ProgressBar("Pybooklet progress").execute(gen)
    assert next(gen) == 1


class MyWidget(ProgressBarWidget):
    
    out = StringIO()

    def render(self, progress, width=None, status=None):
        if status != 'init':
            self.out.write("%s\n" % progress)
        if status is not None:
            self.out.write("%s\n" % status)
        return RenderResult()


class MyPWBar(PBWBar):

    def set_encoding(self, encoding):
        self._bar_ends = ("", "")
        self._bar_symbols = ">"


class MyWidgetFactory(object):

    def __get__(self, progress_bar, owner=None):
        return [PBWString("my widget -> "), MyPWBar(), MyWidget()]


def test_change_default_widgets():

    ProgressBar().execute(progress_fast())

    old = ProgressBar.DEFAULT_WIDGETS
    try:
        ProgressBar.DEFAULT_WIDGETS = MyWidgetFactory()
        ProgressBar().execute(progress_fast())
        lines = MyWidget.out.getvalue().splitlines()
        # print(lines)
        assert len(lines) > 2
        assert lines[0] == "init"
        assert float(lines[1]) < 1e-5 
        assert lines[-1] == "done"
        assert float(lines[-2]) == 1.0
    finally:
        ProgressBar.DEFAULT_WIDGETS = old
    

# This test doesn't really need a connection to H2O cluster.
test_progressbar()
test_change_default_widgets()
