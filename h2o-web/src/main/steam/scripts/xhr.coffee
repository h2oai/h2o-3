Steam.Xhr = (_) ->
  createResponse = (status, data, xhr) ->
    status: status, data: data, xhr: xhr

  handleResponse = (go, jqxhr) ->
    jqxhr
      .done (data, status, xhr) ->
        go null, createResponse status, data, xhr
      .fail (xhr, status, error) ->
        go createResponse status, xhr.responseJSON, xhr

  link$ _.h2oGet, (path, go) ->
    handleResponse go, $.getJSON path

  link$ _.h2oPost, (path, opts, go) ->
    handleResponse go, $.post path, opts
