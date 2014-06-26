# Steam

*Steam* is H<sub>2</sub>O's web client.

## OSX

Install Node.js, dependencies, and build everything.

    brew install node
    npm install -g bower
    npm install -g gulp

To install development dependencies and build the web client, run the top-level gradle build:
    
    cd /h2o-dev
    gradle build -x test

And then point your browser to [http://localhost:54321/steam/index.html](http://localhost:54321/steam/index.html)

## Linux

First, install Node.js by following the instructions on the [Node.js wiki](https://github.com/joyent/node/wiki/Installing-Node.js-via-package-manager), and then:

    npm install -g bower
    npm install -g gulp

To install development dependencies and build the web client, run the top-level gradle build:
    
    cd /h2o-dev
    gradle build -x test


And then point your browser to [http://localhost:54321/steam/index.html](http://localhost:54321/steam/index.html)

## Windows

Step 1. Install Node.js [using the official installer](http://nodejs.org/download/). When done, you should have node.exe and npm.cmd in `\Program Files\node\`. These should also be available on your PATH. If not, add the folder to your PATH.

Step 2. Install `bower` and `gulp` using a windows CMD shell

    npm install -g bower
    npm install -g gulp

Step 3. Run the top-level gradle build:
    
    cd /h2o-dev
    gradle build -x test

And then point your browser to [http://localhost:54321/steam/index.html](http://localhost:54321/steam/index.html)

## Gulp tasks

    Please use `gulp <target>' where <target> is one of -
  	
  	Development tasks:
  	  gulp build      Build and deploy to src/main/resources/www/steam
  	  gulp clean      Clean up build directories
  	  gulp watch      Watch for changes and re-run builds

