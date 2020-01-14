package water.api;

import water.AutoBuffer;
import water.DKV;
import water.ExternalFrameUtils;
import water.fvec.Chunk;
import water.fvec.ChunkUtils;
import water.fvec.Frame;
import water.parser.BufferedString;
import water.server.ServletUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.UUID;

import static water.ExternalFrameUtils.*;
import static water.ExternalFrameUtils.EXPECTED_STRING;

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
    ByteBuffer buffer = ByteBuffer.wrap(selectedColumnsBytes).order(ByteOrder.BIG_ENDIAN);
    int[] selectedColumnIndices = new int[selectedColumnsBytes.length / 4];
    buffer.asIntBuffer().put(selectedColumnIndices);
            
    return new RequestParameters(frameName, chunkId, expectedTypes, selectedColumnIndices); 
  }
  
  private void writeData(RequestParameters parameters, AutoBuffer buffer) {
    Frame frame = DKV.getGet(parameters.frameName);
    Chunk[] chunks = ChunkUtils.getChunks(frame, parameters.chunkId);
    int numberOfRows = chunks[0]._len;
    int[] selectedColumnIndices = parameters.selectedColumnIndices;
    byte[] expectedTypes = parameters.expectedTypes;
    ExternalFrameUtils.sendInt(buffer, numberOfRows);

    // buffered string to be reused for strings to avoid multiple allocation in the loop
    BufferedString valStr = new BufferedString();
    for (int rowIdx = 0; rowIdx < numberOfRows; rowIdx++) {
      for(int i = 0; i < selectedColumnIndices.length; i++){
        if (chunks[selectedColumnIndices[i]].isNA(rowIdx)) {
          ExternalFrameUtils.sendNA(buffer, expectedTypes[i]);
        } else {
          final Chunk chnk = chunks[selectedColumnIndices[i]];
          switch (expectedTypes[i]) {
            case EXPECTED_BOOL:
              ExternalFrameUtils.sendBoolean(buffer, (byte)chnk.at8(rowIdx));
              break;
            case EXPECTED_BYTE:
              ExternalFrameUtils.sendByte(buffer, (byte)chnk.at8(rowIdx));
              break;
            case EXPECTED_CHAR:
              ExternalFrameUtils.sendChar(buffer, (char)chnk.at8(rowIdx));
              break;
            case EXPECTED_SHORT:
              ExternalFrameUtils.sendShort(buffer, (short)chnk.at8(rowIdx));
              break;
            case EXPECTED_INT:
              ExternalFrameUtils.sendInt(buffer, (int)chnk.at8(rowIdx));
              break;
            case EXPECTED_FLOAT:
              ExternalFrameUtils.sendFloat(buffer, (float)chnk.atd(rowIdx));
              break;
            case EXPECTED_LONG:
              ExternalFrameUtils.sendLong(buffer, chnk.at8(rowIdx));
              break;
            case EXPECTED_DOUBLE:
              ExternalFrameUtils.sendDouble(buffer, chnk.atd(rowIdx));
              break;
            case EXPECTED_TIMESTAMP:
              ExternalFrameUtils.sendTimestamp(buffer, chnk.at8(rowIdx));
              break;
            case EXPECTED_STRING:
              String string = null;
              if (chnk.vec().isCategorical()) {
                string = chnk.vec().domain()[(int) chnk.at8(rowIdx)];
              } else if (chnk.vec().isString()) {
                string = chnk.atStr(valStr, rowIdx).toString();
              } else if (chnk.vec().isUUID()) {
                string = new UUID(chnk.at16h(rowIdx), chnk.at16l(rowIdx)).toString();
              } else {
                assert false : "Can never be here";
              }
              ExternalFrameUtils.sendString(buffer, string);
              break;
          }
        }
      }
    }
  }
  
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    String uri = ServletUtils.getDecodedUri(request);
    try {
      RequestParameters parameters = extractRequestParameters(request);
      response.setContentType("application/octet-stream");
      ServletUtils.setResponseStatus(response, HttpServletResponse.SC_OK);
      OutputStream outputStream = response.getOutputStream();
      AutoBuffer buffer = new AutoBuffer(outputStream, false);
      try {
        writeData(parameters, buffer);
      } finally {
        buffer.close();
      }
      buffer.close();
    } catch (Exception e) {
      ServletUtils.sendErrorResponse(response, e, uri);
    } finally {
      ServletUtils.logRequest("GET", request, response);
    }
  }
}
