package playground.clruch.gfx;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;

import javax.swing.JPanel;

import playground.clruch.jmapviewer.interfaces.TileSource;
import playground.clruch.net.SimulationObject;
import playground.clruch.utils.gui.RowPanel;
import playground.clruch.utils.gui.SpinnerLabel;

public class TilesLayer extends ViewerLayer {

    protected TilesLayer(MatsimMapComponent matsimMapComponent) {
        super(matsimMapComponent);
    }

    @Override
    protected void createPanel(RowPanel rowPanel) {
        {
            SpinnerLabel<TileSource> sl = JMapTileSelector.create(matsimMapComponent);
            rowPanel.add(sl.getLabelComponent());
        }
        {
            // JToolBar jToolBar = new JToolBar();
            JPanel jPanel = new JPanel(new FlowLayout(1,2,2));
            {
                SpinnerLabel<Integer> spinnerLabel = new SpinnerLabel<>();
                spinnerLabel.setArray(0, 128, 255);
                spinnerLabel.setValueSafe(matsimMapComponent.mapGrayCover);
                spinnerLabel.addSpinnerListener(i -> {
                    matsimMapComponent.mapGrayCover = i;
                    matsimMapComponent.repaint();
                });
                spinnerLabel.getLabelComponent().setPreferredSize(new Dimension(55, DEFAULT_HEIGHT));
                spinnerLabel.getLabelComponent().setToolTipText("cover gray level");
                jPanel.add(spinnerLabel.getLabelComponent());

            }
            {
                SpinnerLabel<Integer> spinnerLabel = new SpinnerLabel<>();
                spinnerLabel.setArray(0, 32, 64, 96, 128, 160, 192, 255);
                spinnerLabel.setValueSafe(matsimMapComponent.mapAlphaCover);
                spinnerLabel.addSpinnerListener(i -> matsimMapComponent.setMapAlphaCover(i));
                spinnerLabel.getLabelComponent().setPreferredSize(new Dimension(55, DEFAULT_HEIGHT));
                spinnerLabel.getLabelComponent().setToolTipText("cover alpha");
                jPanel.add(spinnerLabel.getLabelComponent());

            }
            rowPanel.add(jPanel);
        }

    }

    @Override
    void paint(Graphics2D graphics, SimulationObject ref) {
    }

    @Override
    void hud(Graphics2D graphics, SimulationObject ref) {
    }

}
