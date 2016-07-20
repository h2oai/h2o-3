# -*- encoding: utf-8 -*-
"""
Handler to an asynchronous task executed on the remote server.

A job is an object with states: CREATED, RUNNING, DONE, FAILED, CANCELLED
A job can be polled for completion and reports the progress so far if it is still RUNNING.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import division, print_function, absolute_import, unicode_literals

import signal
import sys
import time
import warnings

import h2o


class H2OJob(object):
    """A class representing an H2O Job."""

    __PROGRESS_BAR__ = True  # display & update progress bar while polling

    def __init__(self, jobs, job_type):
        if "jobs" in jobs:
            job = jobs["jobs"][0]
        elif "job" in jobs:
            job = jobs["job"]
        else:
            job = jobs

        self.job = job
        self.status = job["status"]
        self.job_key = job["key"]["name"]
        self.dest_key = job["dest"]["name"]
        self.progress = 0
        self._100_percent = False
        self._progress_bar_width = 50
        self._job_type = job_type
        self.exception = job["exception"] if "exception" in job else None
        self._polling = False
        self.warnings = None
        # signal.signal(signal.SIGINT,  self.signal_handler)


    def poll(self):
        """
        Wait until the job finishes.

        This method will continuously query the server about the status of the job, until the job reaches a
        completion. During this time we will display (in stdout) a progress bar with % completion status.

        Poll timing is the following: first we wait 0.2s, then query the server,
        """
        self._polling = True
        poll_interval = 0.2
        start_time = time.time()
        last_poll_time = start_time
        last_display_time = start_time
        last_display_amnt = 0
        width = self._progress_bar_width
        self._update_progress_bar()
        while self._is_running():
            #
            # Lots of algebra here tries to ensure that the progress bar moves smoothly...
            # (it is possible to further improve this by smoothing the speed as well (i.e. control acceleration)).
            #
            next_poll_time = last_poll_time + poll_interval
            current_time = time.time()
            # Estimate when the job will finish. If there is no progress yet, assume it'll be in 2 minutes.
            if self.progress == 0:
                estimated_finish_time = start_time + 120
            else:
                estimated_finish_time = start_time + (last_poll_time - start_time) / self.progress
            if self.progress < 1:
                estimated_finish_time = max(estimated_finish_time, next_poll_time)
            # Figure out when we need to display the next '#' symbol, so that all the remaining symbols will be printed
            # out in a uniform fashion assuming our estimate of finish time is correct.
            symbols_remaining = width - last_display_amnt
            if estimated_finish_time > last_display_time:
                display_speed = symbols_remaining / (estimated_finish_time - last_display_time)
                next_display_time = last_display_time + 1 / max(min(display_speed, 100), 1)
            else:
                display_speed = 0
                next_display_time = next_poll_time + 1  # Force polling before displaying an update
            # Polling should always occur if it is past due -- takes precedence over displaying
            if next_poll_time <= max(current_time, next_display_time):
                if next_poll_time > current_time:
                    time.sleep(next_poll_time - current_time)
                    poll_interval = min(1, poll_interval + 0.2)
                    current_time = time.time()
                last_poll_time = current_time
                self._refresh_job_status()
            else:
                if next_display_time > current_time:
                    time.sleep(next_display_time - current_time)
                    current_time = time.time()
                # Usually `last_display_amnt` will increment by 1, unless progress goes much faster than expected.
                if self.progress == 1:
                    display_incr = symbols_remaining
                else:
                    display_incr = min(symbols_remaining - 1,
                                       int((current_time - last_display_time) * display_speed + 0.1))
                if display_incr > 0:
                    last_display_amnt += display_incr
                    last_display_time = current_time
                    self._update_progress_bar(last_display_amnt)

        self._polling = False
        self._update_progress_bar()
        if self.__PROGRESS_BAR__: print()
        if self.warnings:
            for w in self.warnings:
                warnings.warn(w)
        # check if failed... and politely print relevant message
        if self.status == "CANCELLED":
            raise EnvironmentError("Job with key {} was cancelled by the user.".format(self.job_key))
        if self.status == "FAILED":
            if (isinstance(self.job, dict)) and ("stacktrace" in list(self.job)):
                raise EnvironmentError("Job with key {} failed with an exception: {}\nstacktrace: "
                                       "\n{}".format(self.job_key, self.exception, self.job["stacktrace"]))
            else:
                raise EnvironmentError("Job with key %s failed with an exception: %s" % (self.job_key, self.exception))

        return self

    def poll_once(self):
        print()
        self._refresh_job_status()
        self._update_progress_bar()
        print()

        # check if failed... and politely print relevant message
        if self.status == "CANCELLED":
            raise EnvironmentError("Job with key {} was cancelled by the user.".format(self.job_key))
        if self.status == "FAILED":
            raise EnvironmentError("Job with key {} failed with an exception: {}".format(self.job_key, self.exception))
        return self

    def _refresh_job_status(self):
        jobs = h2o.api("GET /3/Jobs/%s" % self.job_key)
        self.job = jobs["jobs"][0] if "jobs" in jobs else jobs["job"][0]
        self.status = self.job["status"]
        self.progress = min(self.job["progress"], 1)
        self.exception = self.job["exception"]
        self.warnings = self.job["warnings"] if "warnings" in self.job else None

    def _is_running(self):
        return self.status == "RUNNING" or self.status == "CREATED"

    def _update_progress_bar(self, display_amount=None):
        if self._100_percent: return
        if self.progress == 1:
            self._100_percent = True

        if H2OJob.__PROGRESS_BAR__:  # or self._100_percent:
            if display_amount is None:
                display_amount = int(self._progress_bar_width * self.progress)
                progress_pct = int(100 * self.progress + 0.5)
            else:
                progress_pct = int(display_amount / self._progress_bar_width * 100 + 0.5)
            is_unicode = sys.stdout.encoding and sys.stdout.encoding.lower().startswith("utf")
            sym = "█" if is_unicode else "#"
            ends = "||" if is_unicode else "[]"

            space_amount = self._progress_bar_width - display_amount
            the_bar = ends[0] + sym * display_amount + " " * space_amount + ends[1]
            sys.stdout.write("\r%s Progress: %s %02d%%\r" % (self._job_type, the_bar, progress_pct))
            sys.stdout.flush()

    def signal_handler(self, signum, stackframe):
        """(internal)."""
        if self._polling:
            h2o.api("POST /3/Jobs/%s/cancel" % self.job_key)
            print("Job {} was cancelled.".format(self.job_key))
        else:
            signal.default_int_handler()

    def __repr__(self):
        if self.status in {"CREATED", "RUNNING"}:
            desc = "at %d%%" % int(self.progress * 100 + 0.5)
        else:
            desc = self.status.lower()
        return "<H2OJob id=%s %s>" % (self.job_key, desc)
