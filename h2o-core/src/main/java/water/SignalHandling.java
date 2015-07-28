package water;
import sun.misc.Signal;
import sun.misc.SignalHandler;
/**
 * This is a class that prints the SIGs received before the application closes.
 * The reason it is only 4 signals is because the others cause a java.lang.IllegalArgumentException
 * because OS/VM already uses those signals. This program was tested on a Mac.
 */
public class SignalHandling {
    //This method changes the signal names to  java signals in a for loop
    //The for loop calls the handle() method in the  SignalLoggerHandler class
    public static void registerSignalHandlers() {
        String[] sigNames = {"INT", "TERM" ,"HUP", "ABRT"};
        for(String sig: sigNames){
            new SignalLoggerHandler(sig);
        }

    }
}
class SignalLoggerHandler implements SignalHandler{
    final String name;
    //Constructor that takes sets the signal name and creates that type of java signal
    public SignalLoggerHandler(String signalName){
        name  = signalName;
        previousHandler = Signal.handle(new Signal(name), this);
    }
    final SignalHandler previousHandler;
    //this method returns the name of the java signal
    public void handle(Signal signal) {
        System.out.println(signal.getName() + " stopped the program.");
        previousHandler.handle(signal);

    }


}