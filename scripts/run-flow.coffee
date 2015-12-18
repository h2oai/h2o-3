#
# To compile this file:
#     $ coffee -c run-flow.coffee to compile this to run-flow.js
# To run this file: 
#     $ phantomjs run-flow.js --host 172.16.2.82:54321 --flow /path/to/sample_flow.flow
#

system = require 'system'
webpage =  require 'webpage'
fs = require 'fs'

phantom.onError = (message, stacktrace) ->
  if stacktrace?.length
    stack = for t in stacktrace
      ' -> ' + (t.file || t.sourceURL) + ': ' + t.line + (if t.function then ' (in function ' + t.function + ')' else '')
    console.log "HOST: *** ERROR *** #{message}\n" + stack.join '\n'
    phantom.exit 1

printUsageAndExit = (message) ->
  console.log "*** #{message} ***"
  console.log 'Usage: phantomjs run-flow.js [--host ip:port] [--timeout seconds] --flow experiment.flow'
  console.log '    --host ip:port of a running H2O cloud      Defaults to localhost:54321'
  console.log '    --timeout max allowed runtime in seconds   Defaults to forever'
  phantom.exit 1

parseOpts = (args) ->
  console.log "Using args #{args.join ' '}"
  if args.length % 2 is 1
    printUsageAndExit 'Invalid arguments'
  opts = {}
  for key, i in args when i % 2 is 0
    if key[0 .. 1] isnt '--'
      return printUsageAndExit "Expected keyword arg. Found #{key}"
    opts[key] = args[i + 1]
  opts

opts = parseOpts system.args[1..]

hostname = opts['--host'] ? 'localhost:54321'
console.log "Using host #{hostname}"

timeout = if timeoutArg = opts['--timeout']
  timeoutSecs = parseInt timeoutArg, 10
  if isNaN timeoutSecs
    printUsageAndExit "Invalid --timeout: #{timeoutArg}"
  1000 * timeoutSecs
else
  Infinity

console.log "Using timeout #{timeout}ms"

flowFile = opts['--flow']

unless flowFile
  printUsageAndExit 'Expected --flow argument'

console.log "Using Flow #{flowFile}"

try 
  flowContents = fs.read flowFile
catch error
  console.error error 
  phantom.exit 1

page = webpage.create()

page.onResourceError = ({ url, errorString }) ->
  console.log "BROWSER: *** RESOURCE ERROR *** #{url}: #{errorString}"

page.onConsoleMessage = (message) ->
  console.log "BROWSER: #{message}"

waitFor = (test, onReady) ->
  startTime = new Date().getTime()
  isComplete = no
  retest = ->
    if (new Date().getTime() - startTime < timeout) and not isComplete
      console.log 'HOST: Waiting for Flow to complete...'
      isComplete = test()
    else
      if isComplete
        onReady()
        clearInterval interval
      else
        console.log 'HOST: *** ERROR *** Timeout Exceeded'
        phantom.exit 1

  interval = setInterval retest, 2000

page.open "http://#{hostname}/flow/index.html", (status) ->
  if status is 'success'
    test = ->
      page.evaluate(
        (flowContents) ->
          context = window.flow.context
          if window._phantom_started_
            if window._phantom_exit_ then yes else no
          else
            runFlow = (go) ->
              console.log "Opening flow..."
              window._phantom_running_ = yes
              context.open 'Flow', JSON.parse flowContents

              waitForFlow = ->
                if window._phantom_running_
                  setTimeout waitForFlow, 2000
                else
                  console.log 'Flow completed!'
                  errors = window._phantom_errors_
                  go if errors then errors else null

              console.log 'Running flow...'
              context.executeAllCells yes, (status, errors) ->
                console.log "Flow finished with status: #{status}"
                if status is 'failed'
                  window._phantom_errors_ = errors
                window._phantom_running_ = no

              setTimeout waitForFlow, 2000

            console.log 'Running Flow...'

            window._phantom_errors_ = null
            window._phantom_started_ = yes

            runFlow (error) ->
              if error
                console.log '*** ERROR *** Error running Flow'
                window._phantom_errors_ = error.message ? error
              else
                console.log 'Flow execution completed.'
              window._phantom_exit_ = yes
            no
        flowContents
      )

    printErrors = (errors, prefix='') ->
      if errors
        if Array.isArray errors
          (printErrors error, prefix + '  ' for error in errors).join '\n'
        else if errors.message
          if errors.cause
            errors.message + '\n' + printErrors errors.cause, prefix + '  '
          else
            errors.message
        else
          errors
      else
        errors

    waitFor test, ->
      errors = page.evaluate -> window._phantom_errors_
      if errors
        console.log '------------------ FAILED -------------------'
        console.log printErrors errors
        console.log '---------------------------------------------'
        phantom.exit 1
      else
        phantom.exit 0
  else
    console.log 'HOST: *** ERROR *** Unable to access network.'
    phantom.exit 1

