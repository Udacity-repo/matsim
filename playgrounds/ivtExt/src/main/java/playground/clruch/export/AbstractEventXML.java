package playground.clruch.export;

import java.io.File;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Created by Claudio on 1/26/2017.
 */
abstract class AbstractEventXML<Type> {
    String name1;
    String name2;

    AbstractEventXML(String name1in, String name2in){
        name1 = name1in;
        name2 = name2in;
    }

    public void generate(Map<String, NavigableMap<Double, Type>> waitStepFctn, File file){

    };
//    public abstract void generate(Map<String, NavigableMap<Double, Type>> waitStepFctn, File file);

}
