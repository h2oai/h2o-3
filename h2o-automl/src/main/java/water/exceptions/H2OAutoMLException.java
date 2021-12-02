package water.exceptions;

import water.H2OError;

public class H2OAutoMLException extends H2OAbstractRuntimeException {
    
    private final Throwable _rootCause;
    private final int _httpResponse;
    
    public H2OAutoMLException(String msg) {
        this(msg, null);
    }
    
    public H2OAutoMLException(String msg, Throwable rootException) {
        this(msg, rootException, 0);
    }
    
    public H2OAutoMLException(String msg, Throwable rootException, int httpResponse) {
        super(msg, msg);
        _rootCause = rootException;
        _httpResponse = httpResponse > 0 ? httpResponse : super.HTTP_RESPONSE_CODE();
    }

    @Override
    protected int HTTP_RESPONSE_CODE() {
        return _httpResponse;
    }

    @Override
    public H2OError toH2OError(String error_url) {
        H2OError err;
        String rootMessage = _rootCause== null ? null : _rootCause.getMessage().trim();
        if (_rootCause instanceof H2OAbstractRuntimeException) {
            err = ((H2OAbstractRuntimeException) _rootCause).toH2OError(error_url);
        } else {
            err = new H2OError(timestamp, error_url, getMessage(), dev_message, HTTP_RESPONSE_CODE(), values, _rootCause);
        }
        StringBuilder msg = new StringBuilder(getMessage().trim());
        if (msg.charAt(msg.length()-1) != '.') msg.append('.');
        if (rootMessage != null) {
            msg.append(' ');
            msg.append("Root cause: ");
            msg.append(rootMessage);
            err._exception_msg = msg.toString();
        }
        return err;
    }
    
}
