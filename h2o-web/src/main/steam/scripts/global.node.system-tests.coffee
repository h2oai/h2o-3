fs = require 'fs'
path = require 'path'
httpRequest = require 'request'
_ = require('lodash');
test = require('tape');

STEAM_HOST = process.argv[2]

# Check env for STEAM_HOST
STEAM_HOST = process.env.STEAM_HOST unless STEAM_HOST

# Default to localhost
STEAM_HOST = '127.0.0.1:54321' unless STEAM_HOST

# Node.js equivalent of Steam.Xhr
Xhr = (_) ->
  link$ _.invokeH2O = (method, path, go) ->
    url = "http://#{STEAM_HOST}#{path}"
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

createContext = (go) ->
  _ = Steam.ApplicationContext()
  Xhr _
  Steam.H2OProxy _
  _

