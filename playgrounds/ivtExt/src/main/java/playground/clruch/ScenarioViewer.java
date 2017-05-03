package playground.clruch;

import java.io.File;

import org.matsim.api.core.v01.network.Network;

import playground.clruch.gfx.MatsimMapComponent;
import playground.clruch.gfx.MatsimViewerFrame;
import playground.clruch.gfx.PointCloud;
import playground.clruch.gfx.ReferenceFrame;
import playground.clruch.net.MatsimStaticDatabase;
import playground.clruch.netdata.VirtualNetworkGet;
import playground.clruch.utils.NetworkLoader;

/**
 * the viewer allows to connect to the scenario server
 */
public class ScenarioViewer {
    /**
     * @param args
     *            complete path to av_config.xml file, e.g.
     *            /media/bubba/data/ethz/2017_03_13_Sioux_LP_improved/av_config.xml
     */
    public static void main(String[] args) {
        // BEGIN: CUSTOMIZE -----------------------------------------------
        // set manually depending on the scenario:

        ReferenceFrame referenceFrame = ReferenceFrame.SIOUXFALLS;

        // set manually to display virtual network boundary (will be ignored if file doesn't exist)
        // this is optional and should not cause problems if file does not exist.

        File csvFile = new File("vN_40_L1_final/voronoi_BoundaryPoints.csv");

        // END: CUSTOMIZE -------------------------------------------------

        Network network = NetworkLoader.loadNetwork(args);
        MatsimStaticDatabase.initializeSingletonInstance(network, referenceFrame);
        MatsimMapComponent matsimJMapViewer = new MatsimMapComponent(MatsimStaticDatabase.INSTANCE);

        // this is optional and should not cause problems if file does not exist.
        // temporary solution
        matsimJMapViewer.virtualNetworkLayer.setPointCloud(PointCloud.fromCsvFile( //
                csvFile, MatsimStaticDatabase.INSTANCE.referenceFrame.coords_toWGS84));
        matsimJMapViewer.virtualNetworkLayer.setVirtualNetwork(VirtualNetworkGet.readDefault(network));

        MatsimViewerFrame matsimViewer = new MatsimViewerFrame(matsimJMapViewer);
        matsimViewer.setDisplayPosition(MatsimStaticDatabase.INSTANCE.getCenter(), 12);
        matsimViewer.jFrame.setSize(900, 900);
        matsimViewer.jFrame.setVisible(true);
    }

}


//OLD ONE: SAID MASTER UP TO DATE BUT NO TRUE???????? WAS DIFFRENT WHEN SWITCHED TO MASTER AND BACK?
//package playground.clruch;
//
//import java.io.File;
//
//import playground.clruch.gfx.MatsimMapComponent;
//import playground.clruch.gfx.MatsimViewerFrame;
//import playground.clruch.gfx.PointCloud;
//import playground.clruch.gfx.ReferenceFrame;
//import playground.clruch.net.MatsimStaticDatabase;
//import playground.clruch.utils.NetworkLoader;
//
///**
// * the viewer allows to connect to the scenario server
// */
//public class ScenarioViewer {
//    /**
//     * @param args
//     *            complete path to av_config.xml file, e.g.
//     *            /media/bubba/data/ethz/2017_03_13_Sioux_LP_improved/av_config.xml
//     */
//    public static void main(String[] args) {
//        // BEGIN: CUSTOMIZE -----------------------------------------------
//        // set manually depending on the scenario:
//
//        ReferenceFrame referenceFrame = ReferenceFrame.SIOUXFALLS;
//
//        // set manually to display virtual network boundary (will be ignored if file doesn't exist)
//        // this is optional and should not cause problems if file does not exist.
//
//        File csvFile = new File("vN_40_L1_final/voronoi_BoundaryPoints.csv");
//
//        // END: CUSTOMIZE -------------------------------------------------
//
//        MatsimStaticDatabase.initializeSingletonInstance(NetworkLoader.loadNetwork(args), referenceFrame);
//        MatsimMapComponent matsimJMapViewer = new MatsimMapComponent(MatsimStaticDatabase.INSTANCE);
//
//        // this is optional and should not cause problems if file does not exist.
//        // temporary solution
//        matsimJMapViewer.virtualNetworkLayer.setPointCloud(PointCloud.fromCsvFile( //
//                csvFile, MatsimStaticDatabase.INSTANCE.referenceFrame.coords_toWGS84));
//
//        MatsimViewerFrame matsimViewer = new MatsimViewerFrame(matsimJMapViewer);
//        matsimViewer.setDisplayPosition(MatsimStaticDatabase.INSTANCE.getCenter(), 12);
//        matsimViewer.jFrame.setSize(900, 900);
//        matsimViewer.jFrame.setVisible(true);
//    }
//
//}
