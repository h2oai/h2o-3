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
    diag "Calling #{url}"
    opts =
      method: method
      url: url
      timeout: 15000
      #TODO can avoid JSON.parse() step by passing json:true
    httpRequest opts, (error, reply, body) ->
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

_clouds = []
_spawnCloud = ->
  diag "Starting new H2O cloud..."
  cloud = spawn 'java', [ '-Xmx1g', '-jar', JAR_PATH ]
  diag "PID #{cloud.pid}"
  _clouds.push cloud
  cloud

_killCloud = (cloud) ->
  if cloud
    try
      diag "Killing cloud #{cloud.pid}..."
      cloud.kill() # SIGTERM
    catch error
      # noop
  return

killCloud = (cloud) ->
  if -1 < (index = _clouds.indexOf cloud)
    _clouds.splice index, 1
  _killCloud cloud

killAllClouds = ->
  while cloud = _clouds.pop()
    _killCloud cloud
  return

'exit uncaughtException SIGINT SIGTERM SIGHUP SIGBREAK'.split(' ')
  .forEach (signal) ->
    process.on signal, ->
      diag "Caught signal '#{signal}'"
      killAllClouds()

createContext = (host) ->
  _ = Steam.ApplicationContext()
  Xhr _, host
  Steam.H2OProxy _
  _

createCloud = (go) ->
  cloud = _spawnCloud()
  done = -> killCloud cloud

  runTests = (host) ->
    ->
      diag "Executing tests..."
      go (createContext host), done

  cloud.stdout.on 'data', (data) ->
    diag data
    if match = data.toString().match /listen.+http.+http:\/\/(.+)\//i
      host = match[1]
      diag "H2O cloud started at #{host}"
      setTimeout (runTests host), 1000

  cloud.stderr.on 'data', (data) -> diag data
  cloud.on 'close', (code, signal) -> diag "H2O exited with code #{code}, signal #{signal}."

tapediff = (x, y, opts) ->
  _compile = (pattern) ->
    pattern = pattern.replace /\./g, '\\.'
    pattern = pattern.replace /\*{2,}/g, '.+?'
    pattern = pattern.replace /\*/g, '[^.]+?'
    new RegExp '^' + pattern + '$'

  glob = if opts then { include: map((opts.include or []), _compile), exclude: map((opts.exclude or []), _compile) } else null

  _dig = (path) ->
    if glob
      if glob.include.length > 0 and glob.exclude.length > 0
        for include in glob.include
          if include.test path
            for exclude in glob.exclude
              if exclude.test path
                return no
            return yes 
        no
      else if glob.include.length > 0
        for include in glob.include
          return yes if include.test path
        no
      else
        for exclude in glob.exclude
          return no if exclude.test path
        yes
    else
      yes

  _message = (path, x, y) ->
    "Mismatched #{path}: expected '#{x}', actual '#{y}'"

  _diff = (path, x, y) ->
    if (isArray x) and (isArray y)
      subpath = "#{path}#{if path then '.' else ''}#"
      if _dig subpath
        for xi, i in x
          yi = y[i]
          if result = _diff subpath, xi, yi
            return result
      null
    else if (isObject x) and (isObject y)
      for k, vx of x
        subpath = "#{path}#{if path then '.' else ''}#{k}"
        if _dig subpath
          vy = y[k]
          if result = _diff subpath, vx, vy
            return result
      null
    else if (isNaN x) and (isNaN y)
      null
    else
      if x is y then null else _message path, x, y

  _diff '', x, y

tdiff = (t, x, y, opts) ->
  t.fail result if result = tapediff x, y, opts

test 'tapediff', (t) ->
  t.equal (tapediff null, null), null
  t.equal (tapediff null, { foo: 3.1415, bar: 'bar', baz: 'baz' }), 'Mismatched : expected \'null\', actual \'[object Object]\'' 
  t.equal (tapediff { foo: 3.1415, bar: 'bar', baz: 'baz' }, null), 'Mismatched : expected \'[object Object]\', actual \'null\''
  t.equal (tapediff { foo: 3.1415, bar: 'bar', baz: 'baz' }, { foo: 3.1415, bar: 'bar', baz: 'baz' }), null
  t.equal (tapediff { foo: 3.1415, bar: 'bar', baz: Number.NaN }, { foo: 3.1415, bar: 'bar', baz: Number.NaN }), null
  t.equal (tapediff { foo: 3.1415, bar: 'ba', baz: 'baz' }, { foo: 3.1415, bar: 'bar', baz: 'baz' }), 'Mismatched bar: expected \'ba\', actual \'bar\''
  t.equal (tapediff { foo: 3.1415, bar: 'ba', baz: 'baz' }, { foo: 3.1415, bar: 'bar', baz: 'baz' }), 'Mismatched bar: expected \'ba\', actual \'bar\''
  t.equal (tapediff { qux: { foo: 3.1415, bar: 'bar', baz: 'baz' } }, { qux: { foo: 3.1415, bar: 'bar', baz: 'baz' } }), null
  t.equal (tapediff { qux: { foo: 3.1415, bar: 'bar', baz: 'baz' } }, { qux: { foo: 3.1415, bar: 10, baz: 'baz' } }), 'Mismatched qux.bar: expected \'bar\', actual \'10\''
  t.equal (tapediff { qux: { foo: 3.1415, bar: 'bar', baz: 'baz' } }, { qux: { foo: 3.1415, bar: 10, baz: 'baz' } }, { include: ['qux.foo', 'qux.baz'] } ), null
  t.equal (tapediff { qux: { foo: 3.1415, bar: 'bar', baz: 'baz' } }, { qux: { foo: 3.1415, bar: 10, baz: 'baz' } }, { exclude: ['qux.bar'] } ), null
  t.end()




