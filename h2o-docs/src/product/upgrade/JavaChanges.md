#Java Changes

This document describes the changes in the Java API from H2O Classic to H2O 3.0. 

##Unify distribution parameter cross algorithms

The representation of the distribution family has been unified across the H2O code base. The `GBMParameters#_distribution` type has been changed from `GBMModel.GBMParameters.Family` to `hex.Distribution.Family`. The enum `GBMModel.GBMParameters.Family` has been deprecated. Use the enum `hex.Distribution.Family` instead.


##`ValueString#equals` semantics changed

This change affects all comparisons using the form `new ValueString("test") == "test"`. In previous versions of H2O, the method `water.parser.ValueString#equals` was used for comparing Java strings. This method has been deprecated; instead, use the `toString` method to convert the ValueString to a Java string, then compare the results using the `String#equals` method. 

##Start of H2O client app changed 

The method `water.H2OClientApp#start` has been deprecated. Use the `main` method instead. 


##Use of type parameter for `water.Key` unified

All methods accepting or returning `water.Key` have been changed to always accept or return a generic form of `Key<T>`. For example, a signature of the method `Key#make` has been changed to `public static <P extends Keyed> Key<P> make()`. Clients should always use `Key` with a specific target type (e.g., `Key<Frame>`, `Key<Model>`). 


