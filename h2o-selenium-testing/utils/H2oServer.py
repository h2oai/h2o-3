import os
import signal
import subprocess
import time

from selenium import webdriver

from testlibs import common
from utils import se_functions
import Config
from urllib2 import *

# import run
import argparse


class H2oServer:
    run_jar_command = 'java -jar h2o.jar'

    def __init__(self):
        self.process = None
        self.start()

    def start(self):
        print 'Start H2O server'

        pwd_command_process = subprocess.Popen(['pwd'], stdout=subprocess.PIPE)

        file_path = pwd_command_process.stdout.read()
        file_path = file_path[:-1]  # remove end line
        print 'Origin file path: ', file_path

        # h2o_selenium_testing_utils = "h2o-selenium-testing/utils"
        index_str = file_path.index("h2o-selenium-testing")
        print 'index string:', index_str

        file_path_splited = file_path[0: index_str] + "build"

        print 'File path that run H2O server: ', file_path_splited

        self.process = subprocess.Popen(self.run_jar_command, cwd=file_path_splited, shell=True)

        time.sleep(10)

        print 'Start H2O server successfully'

    def stop_by_subprocess(self):
        print 'Stop H2O server by subprocess'

        self.process.terminate()
        self.process.kill()

        print "Stop H2O server successfully"

    def stop_by_terminal(self):
        print 'Stop H2O server by terminal'
        command = "kill $(ps aux | grep '" + self.run_jar_command + "' | awk '{print $2}')"
        subprocess.Popen(command, shell=True)

    def stop_by_UI(self, args):
        print 'Stop H2O server by UI'
        driver = se_functions.open_browser(args)
        common.navigate_to(driver, 'shut_down')
        driver.quit()
        print 'Stop H2O server by UI successfully'

    def restart(self, args):
        print 'Restarting h2o server'
        # todo: refactor it
        self.stop_by_UI(args)
        self.start()
        print 'Restart h2o server successfully'

    def check_website_connect(self, website = Config.h2o_website):
        req = Request(website)
        # Try to open the url
        try:
            urlopen(req)
            print 'FlowUI is available'
            return True
        except HTTPError as e:
            print 'Cannot connect to ', website
            print e.__doc__
            print str(e)
            return False
        except URLError as e:
            print 'Cannot connect to ', website
            print e.__doc__
            print str(e)
            return False


if __name__ == '__main__':
    test = H2oServer()
    # driver = se_functions.get_web_driver('phantomjs', '/home/lamtran/application/phantomjs-1.9.8-linux-i686/bin/phantomjs')
    # test.check_website_connect(website = Config.h2o_website)


    args = argparse.Namespace(browser='phantomjs', location='D:\\application\\phantomjs-1.9.7-windows/phantomjs.exe', testsuite='drf')
    test.stop_by_UI(args)

