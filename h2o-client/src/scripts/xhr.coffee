Steam.Xhr = (_) ->

  createResponse = (status, data, xhr) ->
    status: status, data: data, xhr: xhr

  if exports?

    h2oHttpAddress = process.env.STEAM_NODE_ADDR
    h2oHttpPort = process.env.STEAM_NODE_PORT

    unless h2oHttpAddress
      h2oHttpAddress = '127.0.0.1'
      h2oHttpPort = 54321

    makeHttpRequest = (method, host, port, path, go) ->
      url = "http://#{host}:#{port}#{path}"
      httpRequest url, (error, response, body) ->
        if error
          go error
        else
          go null, createResponse response.statusCode, body, response

    link$ _.invokeH2O, (method, path, go) ->
      makeHttpRequest method, h2oHttpAddress, h2oHttpPort, path, (error, response) ->
        if error
          go error
        else
          switch response.status
            when 200
              try
                json = JSON.parse response.data
                if json
                  go null, createResponse response.status, json, response.xhr
                else
                  go response
              catch error
                go response
            else
              go response

  else

    link$ _.invokeH2O, (method, path, go) ->
      $.getJSON path
        .done (data, status, xhr) ->
          go null, createResponse status, data, xhr
        .fail (xhr, status, error) ->
          go createResponse status, xhr.responseJSON, xhr

