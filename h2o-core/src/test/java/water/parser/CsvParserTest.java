package water.parser;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CsvParserTest {

  private static final String WITH_HEADER = ",loja_estado,funcionario_sexo,funcionario_escolaridade,funcionario_idade,funcionario_distancia_loja,sucesso\n" +
      "0,SP,M,secondary_incomplete,44.5,19154.301574946014,1\n" +
      "1,TO,M,secondary_complete,72.58333333333333,14029.670526387317,0\n" +
      "2,SC,F,higher_incomplete,72.16666666666667,3390.784857751357,0";
  private static final String EMPTY = "";
  
  CsvParser csvParser;
  ParseSetup parseSetup;

  @Before
  public void setUp() throws Exception {
    parseSetup = new ParseSetup();
    parseSetup.setSeparator((byte) 44);
    csvParser = new CsvParser(parseSetup, null);
  }

  @Test
  public void fileHasHeader() {
    final int headerStatus = csvParser.fileHasHeader(WITH_HEADER.getBytes(), parseSetup);
    assertEquals(ParseSetup.HAS_HEADER, headerStatus);
  }

  @Test
  public void fileHasHeader_renamedColumns() {
    parseSetup.setColumnNames(new String[]{"A", "loja_estado", "C", "D", "E", "F", "G"});
    final int headerStatus = csvParser.fileHasHeader(WITH_HEADER.getBytes(), parseSetup);
    assertEquals(ParseSetup.HAS_HEADER, headerStatus);
  }

  @Test
  public void fileHasHeader_wrongCustomHeader() {
    parseSetup.setColumnNames(new String[]{"A", "B"});
    final int headerStatus = csvParser.fileHasHeader(WITH_HEADER.getBytes(), parseSetup);
    assertEquals(ParseSetup.HAS_HEADER, headerStatus);
    assertArrayEquals(new String[]{"", "loja_estado", "funcionario_sexo", "funcionario_escolaridade",
        "funcionario_idade", "funcionario_distancia_loja", "sucesso"}, parseSetup._column_names);
  }

  @Test
  public void emptyFile() {
    final int headerStatus = csvParser.fileHasHeader(EMPTY.getBytes(), parseSetup);
    assertEquals(ParseSetup.NO_HEADER, headerStatus);
  }


}