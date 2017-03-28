package playground.clruch.net;

import java.io.File;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.TreeMap;

import playground.clruch.utils.GlobalAssert;

public enum StorageUtils {
    ;
    // ---
    public static final File OUTPUT = new File("output");
    public static final File DIRECTORY = new File(OUTPUT, "simobj");

    public static File getFileForStorageOf(SimulationObject simulationObject) {
        GlobalAssert.that(OUTPUT.exists());
        DIRECTORY.mkdir();
        File iter = new File(DIRECTORY, String.format("it.%02d", simulationObject.iteration));
        iter.mkdir();
        long floor = (simulationObject.now / 1000) * 1000;
        File folder = new File(iter, String.format("%07d", floor));
        folder.mkdir();
        GlobalAssert.that(folder.exists());
        return new File(folder, String.format("%07d.bin", simulationObject.now));
    }

    public static NavigableMap<Integer, File> getAvailable() {
        if (!DIRECTORY.isDirectory()) {
            System.out.println("no files found");
            return Collections.emptyNavigableMap();
        }
        File[] files = DIRECTORY.listFiles(); // TODO probably not sorted
        if (files.length == 0)
            return Collections.emptyNavigableMap();
        NavigableMap<Integer, File> navigableMap = new TreeMap<>();
        File lastIter = files[files.length - 1];
        for (File dir : lastIter.listFiles())
            if (dir.isDirectory())
                for (File file : dir.listFiles())
                    if (file.isFile())
                        navigableMap.put(Integer.parseInt(file.getName().substring(0, 7)), file);
        return navigableMap;
    }

}
