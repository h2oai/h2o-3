# Flow

*Flow* is H<sub>2</sub>O's web client.

## OSX

Install Node.js, dependencies, and build everything.

    brew install node
    npm install -g bower
    npm install -g gulp

To install development dependencies and build the web client, run the top-level gradle build:
    
    cd /h2o-dev
    ./gradlew build -x test
    java -jar build/h2o.jar

And then point your browser to [http://localhost:54321](http://localhost:54321)

## Linux

First, install Node.js by following the instructions on the [Node.js wiki](https://github.com/joyent/node/wiki/Installing-Node.js-via-package-manager), and then:

    npm install -g bower
    npm install -g gulp

To install development dependencies and build the web client, run the top-level gradle build:
    
    cd /h2o-dev
    ./gradlew build -x test
    java -jar build/h2o.jar


And then point your browser to [http://localhost:54321/](http://localhost:54321/)

## Windows

Step 1. Install Node.js [using the official installer](http://nodejs.org/download/). When done, you should have node.exe and npm.cmd in `\Program Files\node\`. These should also be available on your PATH. If not, add the folder to your PATH.

Step 2. Install `bower` and `gulp` using a windows CMD shell

    npm install -g bower
    npm install -g gulp

Step 3. Run the top-level gradle build:
    
    cd /h2o-dev
    gradlew.bat build -x test
    java -jar build/h2o.jar

And then point your browser to [http://localhost:54321/](http://localhost:54321/)

## Gulp tasks

    Please use `gulp <target>' where <target> is one of -
  	
  	Development tasks:
  	  gulp build      Build and deploy to src/main/resources/www/flow
  	  gulp clean      Clean up build directories
  	  gulp watch      Watch for changes and re-run builds

