package playground.clruch.gfx;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JLabel;

import org.matsim.api.core.v01.Coord;

import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Scalar;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.idsc.tensor.alg.Sort;
import ch.ethz.idsc.tensor.sca.Floor;
import playground.clruch.jmapviewer.JMapViewer;
import playground.clruch.jmapviewer.interfaces.ICoordinate;
import playground.clruch.net.SimulationObject;
import playground.clruch.utils.gui.GraphicsUtil;

public class MatsimJMapViewer extends JMapViewer {

    final MatsimStaticDatabase db;
    private int repaint_count = 0;

    SimulationObject simulationObject = null;

    public final LinkLayer linkLayer;
    public final RequestLayer requestLayer;
    public final VehicleLayer vehicleLayer;
    public final VirtualNetworkLayer virtualNetworkLayer;

    private final List<ViewerLayer> viewerLayers = new ArrayList<>();
    private final List<InfoString> infoStrings = new LinkedList<>();
    private static Font infoStringFont = new Font(Font.MONOSPACED, Font.BOLD, 13);
    private static Font debugStringFont = new Font(Font.SERIF, Font.PLAIN, 8);

    public JLabel jLabel = new JLabel(" ");

    public MatsimJMapViewer(MatsimStaticDatabase db) {
        this.db = db;

        linkLayer = new LinkLayer(this);
        requestLayer = new RequestLayer(this);
        vehicleLayer = new VehicleLayer(this);
        virtualNetworkLayer = new VirtualNetworkLayer(this);

        viewerLayers.add(linkLayer);
        viewerLayers.add(requestLayer);
        matsimHeatmaps.add(requestLayer.requestHeatMap);
        matsimHeatmaps.add(requestLayer.requestDestMap);
        viewerLayers.add(vehicleLayer);
        viewerLayers.add(virtualNetworkLayer);
    }

    /**
     * 
     * @param coord
     * @return null of coord is not within view
     */
    final Point getMapPosition(Coord coord) {
        return getMapPosition(coord.getY(), coord.getX());
    }

    final Point getMapPositionAlways(Coord coord) {
        return getMapPosition(coord.getY(), coord.getX(), false);
    }

    final Coord getCoordPositionXY(Point point) {
        ICoordinate ic = getPosition(point);
        // System.out.println("lat=" + ic.getLat() + " lon=" + ic.getLon());
        Coord coord = new Coord(ic.getLon(), ic.getLat());
        Coord xy = db.referenceFrame.coords_fromWGS84.transform(coord);
        // System.out.println(xy);
        return xy;
    }

    @Override
    protected void paintComponent(Graphics g) {
        ++repaint_count;
        final SimulationObject ref = simulationObject; // <- use ref for thread safety

        if (ref != null)
            viewerLayers.forEach(viewerLayer -> viewerLayer.prepareHeatmaps(ref));

        super.paintComponent(g);

        final Graphics2D graphics = (Graphics2D) g;
        final Dimension dimension = getSize();

        {

            Coord NW = getCoordPositionXY(new Point(0, 0));
            Coord NE = getCoordPositionXY(new Point(dimension.width, 0));
            Coord SW = getCoordPositionXY(new Point(0, dimension.height));
            Coord SE = getCoordPositionXY(new Point(dimension.width, dimension.height));

            Tensor X = Sort.of(Tensors.vectorDouble(NW.getX(), NE.getX(), SW.getX(), SE.getX()));
            Tensor Y = Sort.of(Tensors.vectorDouble(NW.getY(), NE.getY(), SW.getY(), SE.getY()));

            Scalar minX = X.Get(0);
            Scalar maxX = X.Get(3);
            Scalar minY = Y.Get(0);
            Scalar maxY = Y.Get(3);

            double log = Math.floor(Math.log10(maxX.subtract(minX).number().doubleValue()));
            Scalar dX = RealScalar.of(Math.pow(10, log));
            // Floor.of(Log.function.apply().divide(Log.function.apply(RealScalar.of(10))));
            // Scalar dX = (Scalar) Floor.of(maxX.subtract(minX).divide(RealScalar.of(100))).multiply(RealScalar.of(10));
            Scalar dY = (Scalar) Floor.of(maxY.subtract(minY).divide(RealScalar.of(100))).multiply(RealScalar.of(10));
            System.out.println(dX);

            Scalar ofsX = (Scalar) Floor.of(minX.divide(dX)).multiply(dX);
            Scalar ofsY = (Scalar) Floor.of(minY.divide(dY)).multiply(dY);

            graphics.setColor(Color.RED);
            for (int i = 0; i < 10; ++i) {
                Point prev = null;
                for (int j = 0; j < 10; ++j) {
                    Scalar pX = ofsX.add(dX.multiply(RealScalar.of(i)));
                    Scalar pY = ofsY.add(dY.multiply(RealScalar.of(j)));
                    Coord mat = db.referenceFrame.coords_toWGS84.transform( //
                            new Coord(pX.number().doubleValue(), pY.number().doubleValue()));
                    Point point = getMapPositionAlways(mat);
                    graphics.fillRect(point.x, point.y, 2, 2);
                    if (prev != null) {
                        graphics.drawLine(prev.x, prev.y, point.x, point.y);
                    }
                    prev = point;
                }
            }
        }

        if (ref != null) {

            infoStrings.clear();
            append(new SecondsToHMS(ref.now).toDigitalWatch());
            appendSeparator();

            viewerLayers.forEach(viewerLayer -> viewerLayer.paint(graphics, ref));
            viewerLayers.forEach(viewerLayer -> viewerLayer.hud(graphics, ref));

            append("%5d zoom", getZoom());
            append("%5d m/pixel", (int) Math.ceil(getMeterPerPixel()));
            appendSeparator();

            jLabel.setText(ref.infoLine);

            drawInfoStrings(graphics);
            GraphicsUtil.setQualityHigh(graphics);
            new SbbClockDisplay().drawClock(graphics, ref.now, new Point(dimension.width - 70, 70));
        }
        {
            graphics.setFont(debugStringFont);
            graphics.setColor(Color.LIGHT_GRAY);
            graphics.drawString("" + repaint_count, 0, dimension.height - 40);
        }
    }

    private void drawInfoStrings(Graphics2D graphics) {
        int piy = 10;
        final int pix = 5;
        final int height = 15;
        graphics.setFont(infoStringFont);
        FontMetrics fontMetrics = graphics.getFontMetrics();
        for (InfoString infoString : infoStrings) {
            if (infoString.message.isEmpty()) {
                piy += height * 2 / 3;
            } else {
                graphics.setColor(new Color(255, 255, 255, 192));
                int width = fontMetrics.stringWidth(infoString.message);
                graphics.fillRect(0, piy, pix + width + 1, height);
                graphics.setColor(infoString.color);
                graphics.drawString(infoString.message, pix, piy + height - 2);

                piy += height;
            }
        }
    }

    void appendSeparator() {
        append(new InfoString(""));
    }

    void append(String format, Object... args) {
        append(new InfoString(String.format(format, args)));
    }

    void append(InfoString infoString) {
        infoStrings.add(infoString);
    }

    public void setSimulationObject(SimulationObject simulationObject) {
        this.simulationObject = simulationObject;
        repaint();
    }

    public void setMapAlphaCover(int alpha) {
        mapAlphaCover = alpha;
        repaint();
    }

}
