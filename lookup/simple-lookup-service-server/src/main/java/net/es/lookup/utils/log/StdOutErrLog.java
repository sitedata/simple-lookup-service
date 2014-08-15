package net.es.lookup.utils.log;

import org.apache.log4j.Logger;

import java.io.PrintStream;

/**
 * Author: sowmya
 * Date: 8/15/14
 * Time: 10:34 AM
 */
public class StdOutErrLog {

    private static Logger LOG = Logger.getLogger(StdOutErrLog.class);

    public static void redirectStdOutErrToLog(){
        System.setOut(createLoggingPrintStream(System.out));
        System.setErr(createLoggingPrintStream(System.err));

        System.out.println("Hi");
    }

    private static PrintStream createLoggingPrintStream(final PrintStream printStream) {

        return new PrintStream(printStream){

            public void print(String s){
                LOG.debug(s);
            }

            public void print(Object o){
                LOG.debug(o);
            }

            public void println(String s){
                LOG.debug(s);
            }

            public void println(Object o){
                LOG.debug(o);
            }


        };

    }

}
