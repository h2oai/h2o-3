package water.api;


import water.Iced;

public class AssemblyV99 extends RequestSchema<Iced, AssemblyV99>  {

  // input fields
  @API(help="A list of steps describing the assembly line.") String[] steps;
  @API(help="Input Frame for the assembly.") KeyV3.FrameKeyV3 frame;

  //output
  @API(help="Output of the assembly line.") KeyV3.FrameKeyV3 result;
  @API(help="A Key to the fit Assembly data structure") KeyV3.AssemblyKeyV3 assembly;
}