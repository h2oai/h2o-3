#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Pyunit for h2o.utils.progressbar."""
from __future__ import absolute_import, division, print_function

import math
import random
import time

from h2o.utils.progressbar import ProgressBar, PBWBar, PBWPercentage


def test_progressbar():
    """Test functionality for the progress bar."""
    def progress_generator(duration):
        interval = duration / 20
        for i in range(20):
            yield (i + 1) / 20, interval

    ProgressBar().execute(progress_generator(5))
    ProgressBar("With file_mode", file_mode=True).execute(progress_generator(5))
    ProgressBar(widgets=["Clowncopterization in progress, stand WAY back!", PBWBar(), PBWPercentage()])\
        .execute(progress_generator(3))

    def random_progress_generator(duration, interrupted=False):
        progress = 0
        n_steps = 10
        last_t = time.time()
        beta = n_steps / duration
        while progress < n_steps:
            delta = time.time() - last_t
            last_t = time.time()
            if interrupted and random.random() > math.exp(-beta * delta / (n_steps / 4)):
                raise StopIteration("planets did not align properly")
            if random.random() > math.exp(-beta * delta):
                progress += 1
            yield progress / n_steps

    ProgressBar("Random 1s").execute(random_progress_generator(1))
    ProgressBar("Random 5s").execute(random_progress_generator(5))
    ProgressBar("Random 10s").execute(random_progress_generator(10))
    ProgressBar("Hope this one works").execute(random_progress_generator(5, True))



# This test doesn't really need a connection to H2O cluster.
test_progressbar()
