# Steam

*Steam* is H<sub>2</sub>O's web client.

## OSX

Install Node.js, dependencies, and build everything.

    brew install node
    npm install -g bower
    cd h2o/client
    make setup build

And then point your browser to [http://localhost:54321/steam/index.html](http://localhost:54321/steam/index.html)

## Linux

First, install Node.js by following the instructions on the [Node.js wiki](https://github.com/joyent/node/wiki/Installing-Node.js-via-package-manager), and then:

    npm install -g bower
    cd h2o/client
    make setup build

And then point your browser to [http://localhost:54321/steam/index.html](http://localhost:54321/steam/index.html)

## Windows

*Note: You'll need a working cygwin environment. You probably have one already for building h2o.*

Step 1. Install Node.js [using the official installer](http://nodejs.org/download/). When done, you should have node.exe and npm.cmd in `\Program Files\node\`. These should also be available on your PATH. If not, add the folder to your PATH.

Step 2. Install `bower`

    npm install -g bower


Step 3. Install the required `npm` and `bower` packages (outside cygwin):

    cd \h2o\client
    npm install
    bower install

Step 4. Now, from your cygwin prompt, run:

    cd /h2o/client
    make build

*Note: On OSX/Linux, you would normally run Step 3 as `make setup`, which would in turn run `npm install` and `bower install`. However, running npm from within cygwin currently has issues: see [#3710](https://github.com/npm/npm/issues/3710)).*

And then point your browser to [http://localhost:54321/steam/index.html](http://localhost:54321/steam/index.html)

## Make tasks

Run `make help` to get a list of `make` tasks.

    $ make help

    Please use `make <target>' where <target> is one of -

  	Setup tasks:
  	  make check      Check prerequisites
  	  make setup      Set up dev dependencies
  	  make reset      Clean up dev dependencies
  	  make preload    Preload a few frames and models into H2O (requires R)
  	
  	Development tasks:
  	  make build      Build and deploy to ../lib/resources/steam/
  	  make unit       Build browser test suite
  	  make test       Run all tests
  	  make            Run all tests
  	  make smoke      Run tests, but bail on first failure
  	  make report     Run all tests, verbose, with specs
  	  make debug      Run tests in debug mode
  	  make spec       Compile test specs
  	  make coverage   Compile test coverage
  	  make doc        Compile code documentation
  	  make clean      Clean up build directories
  	  make watch      Watch for changes and run `make build test`

