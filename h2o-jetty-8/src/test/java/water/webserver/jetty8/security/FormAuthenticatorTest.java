package water.webserver.jetty8.security;

import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FormAuthenticatorTest {
    
    @Test
    public void extractJUri_original() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("https://localhost:54321/3/Model"));
        when(request.getQueryString()).thenReturn("model_id=my_model");

        FormAuthenticator authenticator = new FormAuthenticator("/login", "/error", false, false);
        StringBuffer sb = authenticator.extractJUri(request);
        
        assertEquals("https://localhost:54321/3/Model?model_id=my_model", sb.toString());
        verify(request, times(1)).getRequestURL();
        verify(request, times(2)).getQueryString();
        verifyNoMoreInteractions(request);
    }

    @Test
    public void extractJUri_relative() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContextPath()).thenReturn("/3/Model");
        when(request.getQueryString()).thenReturn("model_id=my_model");

        FormAuthenticator authenticator = new FormAuthenticator("/login", "/error", false, true);
        StringBuffer sb = authenticator.extractJUri(request);

        assertEquals("/3/Model?model_id=my_model", sb.toString());
        verify(request, times(1)).getContextPath();
        verify(request, times(2)).getQueryString();
        verifyNoMoreInteractions(request);
    }

}
