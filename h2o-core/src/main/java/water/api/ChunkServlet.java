package water.api;

import water.ChunkAutoBufferWriter;
import water.server.ServletUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Base64;

/**
 * This servlet class handles GET requests for the path /3/Chunk
 * It requires 4 get parameters
 * - frame_name - a unique string identifier of H2O Frame
 * - chunk_id - a unique identifier of the chunk within the H2O Frame
 * - expected_type - byte array encoded in Base64 encoding. The types corresponds to the `selected_columns` parameter
 * - selected_columns - selected columns indices encoded int Base64 encoding.
 * The result is represented as a stream of binary data. Data are encoded to AutoBuffer row by row. 
 * The data stream starts with the integer representing the number of rows.
 */
public class ChunkServlet extends HttpServlet {

  private static class RequestParameters {
    final String frameName;
    final int chunkId;
    final byte[] expectedTypes;
    final int[] selectedColumnIndices;
    
    public RequestParameters(String frameName, int chunkId, byte[] expectedTypes, int[] selectedColumnIndices) {
      this.frameName = frameName;
      this.chunkId = chunkId;
      this.expectedTypes = expectedTypes;
      this.selectedColumnIndices = selectedColumnIndices;
    }
  }
  
  private String getParameterAsString(HttpServletRequest request, String parameterName) {
    String result = request.getParameter(parameterName);
    if (result == null) {
      throw new RuntimeException(String.format("Cannot find value for parameter \'%s\'", parameterName));
    }
    return result;
  }
  
  private RequestParameters extractRequestParameters(HttpServletRequest request) {
    String frameName = getParameterAsString(request, "frame_name");
    
    String chunkIdString = getParameterAsString(request, "chunk_id");
    int chunkId = Integer.parseInt(chunkIdString);
    
    String expectedTypesString = getParameterAsString(request, "expected_types");
    byte[] expectedTypes = Base64.getDecoder().decode(expectedTypesString);

    String selectedColumnsString = getParameterAsString(request, "selected_columns");
    byte[] selectedColumnsBytes = Base64.getDecoder().decode(selectedColumnsString);
    IntBuffer buffer = ByteBuffer.wrap(selectedColumnsBytes).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
    int[] selectedColumnIndices = new int[buffer.remaining()];
    buffer.get(selectedColumnIndices);
            
    return new RequestParameters(frameName, chunkId, expectedTypes, selectedColumnIndices); 
  }
  
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    String uri = ServletUtils.getDecodedUri(request);
    try {
      RequestParameters parameters = extractRequestParameters(request);
      response.setContentType("application/octet-stream");
      ServletUtils.setResponseStatus(response, HttpServletResponse.SC_OK);
      OutputStream outputStream = response.getOutputStream();
      ChunkAutoBufferWriter writer = new ChunkAutoBufferWriter(outputStream);
      try {
        writer.writeChunk(
            parameters.frameName, 
            parameters.chunkId, 
            parameters.expectedTypes,
            parameters.selectedColumnIndices);
      } finally {
        writer.close();
      }
    } catch (Exception e) {
      ServletUtils.sendErrorResponse(response, e, uri);
    } finally {
      ServletUtils.logRequest("GET", request, response);
    }
  }
}
