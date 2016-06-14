package water.api;


import water.Iced;

/** FIXME: comments please! */
public class AssemblyV99 extends SchemaV3<Iced, AssemblyV99>  {

  // input fields
  @API(help="A list of steps describing the assembly line.") String[] steps;
  @API(help="Input Frame for the assembly.") KeyV3.FrameKeyV3 frame;
  @API(help="The name of the file and generated class ") String pojo_name;
  @API(help="The key of the Assembly object to retrieve from the DKV.") String assembly_id;

  //output
  @API(help="Output of the assembly line.") KeyV3.FrameKeyV3 result;
  @API(help="A Key to the fit Assembly data structure") KeyV3.AssemblyKeyV3 assembly;
}