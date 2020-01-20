package water.api;

import water.ChunkAutoBufferWriter;
import water.DKV;
import water.ExternalFrameUtils;
import water.fvec.Frame;
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
public final class ChunkServlet extends HttpServlet {

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
  
  private String getParameterAsString(final HttpServletRequest request, final String parameterName) {
    final String result = request.getParameter(parameterName);
    if (result == null) {
      throw new RuntimeException(String.format("Cannot find value for parameter \'%s\'", parameterName));
    }
    return result;
  }
  
  private RequestParameters parseRequestParameters(final HttpServletRequest request) {
    final String frameName = getParameterAsString(request, "frame_name");
    
    final String chunkIdString = getParameterAsString(request, "chunk_id");
    final int chunkId = Integer.parseInt(chunkIdString);
    
    final String expectedTypesString = getParameterAsString(request, "expected_types");
    final byte[] expectedTypes = Base64.getDecoder().decode(expectedTypesString);

    final String selectedColumnsString = getParameterAsString(request, "selected_columns");
    final byte[] selectedColumnsBytes = Base64.getDecoder().decode(selectedColumnsString);
    final IntBuffer buffer = ByteBuffer.wrap(selectedColumnsBytes).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
    final int[] selectedColumnIndices = new int[buffer.remaining()];
    buffer.get(selectedColumnIndices);
            
    return new RequestParameters(frameName, chunkId, expectedTypes, selectedColumnIndices); 
  }
  
  private void validateRequestParameters(final RequestParameters parameters) {
    final Frame frame = DKV.getGet(parameters.frameName);
    if (frame == null) {
      throw new RuntimeException(String.format("A frame with name '%s' doesn't exist.", parameters.frameName));
    }

    validateChunkId(parameters, frame);
    validateSelectedColumns(parameters, frame);
    validateExpectedTypes(parameters, frame);
  }
  
  private void validateChunkId(final RequestParameters parameters, final Frame frame) {
    if (parameters.chunkId < 0) {
      throw new RuntimeException(String.format("chunk_id can't be negative. Current value: %d", parameters.chunkId));
    }
    
    final int numberOfChunks = frame.anyVec().nChunks();
    if (parameters.chunkId >= numberOfChunks) {
      final String message = String.format(
        "chunk_id '%d' is out of range. The frame '%s' has %d chunks.",
        parameters.chunkId,
        parameters.frameName,
        numberOfChunks);
      throw new RuntimeException(message);
    }
  }

  private void validateSelectedColumns(final RequestParameters parameters, final Frame frame) {
    for (int i = 0; i < parameters.selectedColumnIndices.length; i++) {
      if (parameters.selectedColumnIndices[i] < 0) {
        final String message = String.format(
          "Selected column index ('selected_columns') at position %d with the value '%d' is negative.",
          i,
          parameters.selectedColumnIndices[i]);
        throw new RuntimeException(message);
      }
      if (parameters.selectedColumnIndices[i] >= frame.numCols()) {
        final String message = String.format(
          "Selected column index ('selected_columns') at position %d with the value '%d' is out of range. Frame '%s' has %d columns.",
          i,
          parameters.selectedColumnIndices[i],
          parameters.frameName,
          frame.numCols());
        throw new RuntimeException(message);
      }
    }    
  }

  private void validateExpectedTypes(final RequestParameters parameters, final Frame frame) {
    for (int i = 0; i < parameters.expectedTypes.length; i++) {
      if (parameters.expectedTypes[i] < ExternalFrameUtils.EXPECTED_BOOL ||
          parameters.expectedTypes[i] > ExternalFrameUtils.EXPECTED_VECTOR) {
        final String message = String.format(
          "Expected Type ('expected_types') at position %d with the value '%d' is invalid.",
          i,
          parameters.expectedTypes[i]);
        throw new RuntimeException(message);
      }
    }
    
    if (parameters.selectedColumnIndices.length != parameters.expectedTypes.length) {
      final String message = String.format(
        "The number of expected_types '%d' is not the same as the number of selected_columns '%d'",
        parameters.expectedTypes.length,
        parameters.selectedColumnIndices.length);
      throw new RuntimeException(message);
    }
    
    final byte[] expectedVecTypes = new byte[parameters.expectedTypes.length];
    final byte[] frameTypes = frame.types();
    for (int i = 0; i < parameters.expectedTypes.length; i++) {
      final int frameTypePosition = parameters.selectedColumnIndices[i];  
      if (expectedVecTypes[i] != frameTypes[frameTypePosition]) {
        final String message = String.format(
          "Expected Type ('expected_types') at position %d doesn't correspond to the frame column type at position %d.",
          i,
          frameTypePosition);
        throw new RuntimeException(message);
      }
    }
  }
  
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    final String uri = ServletUtils.getDecodedUri(request);
    try {
      final RequestParameters parameters = parseRequestParameters(request);
      validateRequestParameters(parameters);
      response.setContentType("application/octet-stream");
      ServletUtils.setResponseStatus(response, HttpServletResponse.SC_OK);
      try (OutputStream outputStream = response.getOutputStream();
           ChunkAutoBufferWriter writer = new ChunkAutoBufferWriter(outputStream)) {
        writer.writeChunk(
          parameters.frameName,
          parameters.chunkId,
          parameters.expectedTypes,
          parameters.selectedColumnIndices);
      }
    } catch (Exception e) {
      ServletUtils.sendErrorResponse(response, e, uri);
    } finally {
      ServletUtils.logRequest(request.getMethod(), request, response);
    }
  }
}
