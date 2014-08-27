argv = (require 'minimist') process.argv.slice 2
fs = require 'fs'
path = require 'path'
spawn = (require 'child_process').spawn
httpRequest = require 'request'
_ = require 'lodash'
test = require 'tape'

# TAP diagnostics
diag = (message) -> console.log '# ' + message

# TAP bail out
bailout = (message) -> console.log 'Bail out! ' + message

# Pass -jar /path/to/h2o.jar to override the default jar
JAR_PATH = if argv.jar then path.resolve argv.jar else path.resolve process.cwd(), '..', path.join 'h2o-app', 'build', 'libs', 'h2o-app.jar'

throw "H2O jar '#{jarpath}' not found!" unless fs.existsSync JAR_PATH

# Node.js equivalent of Steam.Xhr
Xhr = (_, host) ->
  link$ _.invokeH2O, (method, path, go) ->
    url = "http://#{host}#{path}"
    httpRequest url, (error, reply, body) ->
      if error
        go error
      else
        response = status: reply.statusCode, data: body, xhr: reply
        switch response.status
          when 200
            try
              json = JSON.parse response.data
              if json
                go null, status: response.status, data: json, xhr: response.xhr
              else
                go response
            catch error
              go response
          else
            go response

createContext = (host) ->
  _ = Steam.ApplicationContext()
  Xhr _, host
  Steam.H2OProxy _
  _

createCloud = (go) ->
  diag "Starting new H2O cloud..."
  h2o = spawn 'java', [ '-Xmx1g', '-jar', JAR_PATH ]
  diag "PID #{h2o.pid}"

  done = ->
    diag "Killing H2O..."
    h2o.kill() # SIGTERM

  runTests = (host) ->
    ->
      diag "Executing tests..."
      go (createContext host), done

  h2o.stdout.on 'data', (data) ->
    diag data
    if match = data.toString().match /listen.+http.+http:\/\/(.+)\//i
      host = match[1]
      diag "H2O cloud started at #{host}"
      setTimeout (runTests host), 1000

  h2o.stderr.on 'data', (data) -> diag data
  h2o.on 'close', (code, signal) -> diag "H2O exited with code #{code}, signal #{signal}."
