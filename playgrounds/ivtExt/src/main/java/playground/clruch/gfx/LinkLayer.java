package playground.clruch.gfx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Scalar;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.idsc.tensor.ZeroScalar;
import ch.ethz.idsc.tensor.alg.Dimensions;
import ch.ethz.idsc.tensor.alg.Subdivide;
import ch.ethz.idsc.tensor.io.ObjectFormat;
import ch.ethz.idsc.tensor.red.Total;
import playground.clruch.gheat.graphics.Hue;
import playground.clruch.net.OsmLink;
import playground.clruch.net.SimulationObject;
import playground.clruch.utils.LruCache;
import playground.clruch.utils.gui.GraphicsUtil;
import playground.clruch.utils.gui.RowPanel;
import playground.clruch.utils.gui.SpinnerLabel;

public class LinkLayer extends ViewerLayer {

    private volatile boolean drawLoad = false;
    private volatile boolean drawLinks = false;
    private volatile boolean drawMaxCars = false;
    private static final Color LINKCOLOR = new Color(153, 153, 102, 64);

    Map<Long, SimulationObject> lruCache = LruCache.create(10);

    int historyLength = 4;
    int loadScale = 3;
    Tensor matrix = null; //

    public LinkLayer(MatsimMapComponent matsimMapComponent) {
        super(matsimMapComponent);

        {
            File file = new File("output/linkstats.obj");
            if (file.isFile())
                try {
                    matrix = ObjectFormat.parse(Files.readAllBytes(file.toPath()));
                    System.out.println("loaded: " + Dimensions.of(matrix));
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
        }
    }

    @Override
    void paint(Graphics2D graphics, SimulationObject ref) {
        lruCache.put(ref.now, ref);

        if (drawLinks) {
            graphics.setColor(LINKCOLOR);
            for (OsmLink osmLink : matsimMapComponent.db.getOsmLinks()) {
                Point p1 = matsimMapComponent.getMapPosition(osmLink.getCoordFrom());
                if (p1 != null) {
                    Point p2 = matsimMapComponent.getMapPositionAlways(osmLink.getCoordTo());
                    graphics.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
            }
        }

        if (drawLoad) {
            int width = historyLength;
            LinkStats linkStats = new LinkStats(width);
            linkStats.feed(ref, 0);
            int DT = MatsimViewerFrame.STEPSIZE_SECONDS;
            for (int ofs = 1; ofs < width; ++ofs)
                if (lruCache.containsKey(ref.now - DT * ofs))
                    linkStats.feed(lruCache.get(ref.now - DT * ofs), ofs);

            final Tensor table = linkStats.count;
            Tensor weight;
            if (1 < width) {
                weight = Subdivide.of(RealScalar.of(1), RealScalar.of(.1), width - 1);
                weight = weight.multiply(Total.of(weight).Get().invert());
            } else {
                weight = Tensors.of(RealScalar.ONE);
            }

            GraphicsUtil.setQualityHigh(graphics);
            // System.out.println(linkStats.linkIndex.size());
            double scaling = loadScale * 1000;
            for (int index : linkStats.linkIndex) {
                OsmLink osmLink = matsimMapComponent.db.getOsmLink(index);
                final double factor = osmLink.getLength() * matsimMapComponent.getMeterPerPixel();
                Point p1 = matsimMapComponent.getMapPosition(osmLink.getCoordFrom());
                if (p1 != null) {
                    Point p2 = matsimMapComponent.getMapPositionAlways(osmLink.getCoordTo());
                    Tensor linkTable = weight.dot(table.get(index));
                    final double total = Total.of(linkTable).Get().number().doubleValue();
                    final double carsEmpty = linkTable.Get(1).number().doubleValue();
                    // System.out.println(total + " " + carsEmpty);
                    double h = (carsEmpty / (double) total + 0.8) / 3;
                    graphics.setColor(new Hue(h, 1, .85, .75).rgba);
                    Stroke stroke = new BasicStroke((float) Math.sqrt(scaling * total / factor));
                    graphics.setStroke(stroke);
                    Shape shape = new Line2D.Double(p1.x, p1.y, p2.x, p2.y);
                    graphics.draw(shape);
                }
            }
            GraphicsUtil.setQualityDefault(graphics);
            graphics.setStroke(new BasicStroke());
        }

        if (drawMaxCars && matrix != null) {
            graphics.setColor(Color.DARK_GRAY);
            int index = 0;
            for (OsmLink osmLink : matsimMapComponent.db.getOsmLinks()) {
                Point middle = matsimMapComponent.getMapPosition(osmLink.getAt(.3333));
                if (middle != null) {
                    Scalar carC = matrix.Get(index, 1);
                    if (!carC.equals(ZeroScalar.get()))
                        graphics.drawString("" + carC, middle.x, middle.y);
                }
                ++index;
            }
        }

    }

    @Override
    void hud(Graphics2D graphics, SimulationObject ref) {
        if (drawLinks) {
            matsimMapComponent.append("%5d streets", matsimMapComponent.db.getOsmLinksSize());
            matsimMapComponent.appendSeparator();
        }
    }

    public void setDraw(boolean selected) {
        drawLinks = selected;
        matsimMapComponent.repaint();
    }

    public void setDrawMaxCars(boolean selected) {
        drawMaxCars = selected;
        matsimMapComponent.repaint();
    }

    @Override
    protected void createPanel(RowPanel rowPanel) {
        {
            final JCheckBox jCheckBox = new JCheckBox("load");
            jCheckBox.setToolTipText("width proportional to number of vehicles on link");
            jCheckBox.setSelected(drawLoad);
            jCheckBox.addActionListener(event -> {
                drawLoad = jCheckBox.isSelected();
                matsimMapComponent.repaint();
            });
            rowPanel.add(jCheckBox);
        }
        {
            JPanel jPanel = new JPanel(new FlowLayout(1, 2, 2));
            {
                SpinnerLabel<Integer> spinnerLabel = new SpinnerLabel<>();
                spinnerLabel.setArray(1, 2, 3, 4, 5);
                spinnerLabel.setValueSafe(historyLength);
                spinnerLabel.addSpinnerListener(i -> {
                    historyLength = i;
                    matsimMapComponent.repaint();
                });
                spinnerLabel.getLabelComponent().setPreferredSize(new Dimension(55, DEFAULT_HEIGHT));
                spinnerLabel.getLabelComponent().setToolTipText("load history length");
                jPanel.add(spinnerLabel.getLabelComponent());

            }
            {
                SpinnerLabel<Integer> spinnerLabel = new SpinnerLabel<>();
                spinnerLabel.setArray(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
                spinnerLabel.setValueSafe(loadScale);
                spinnerLabel.addSpinnerListener(i -> {
                    loadScale = i;
                    matsimMapComponent.repaint();
                });
                spinnerLabel.getLabelComponent().setPreferredSize(new Dimension(55, DEFAULT_HEIGHT));
                spinnerLabel.getLabelComponent().setToolTipText("load scaling");
                jPanel.add(spinnerLabel.getLabelComponent());

            }
            rowPanel.add(jPanel);
        }
        {
            final JCheckBox jCheckBox = new JCheckBox("streets");
            jCheckBox.setToolTipText("each link as thin line");
            jCheckBox.setSelected(drawLinks);
            jCheckBox.addActionListener(event -> setDraw(jCheckBox.isSelected()));
            rowPanel.add(jCheckBox);
        }
        {
            final JCheckBox jCheckBox = new JCheckBox("maxCars");
            jCheckBox.setSelected(drawMaxCars);
            jCheckBox.addActionListener(event -> setDrawMaxCars(jCheckBox.isSelected()));
            rowPanel.add(jCheckBox);
        }

    }

}
