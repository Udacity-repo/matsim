package playground.joel.analysis;

import java.awt.Color;
import java.io.File;

import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Tensors;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import ch.ethz.idsc.tensor.Tensor;
import playground.clruch.utils.GlobalAssert;

/**
 * Created by Joel on 04.03.2017.
 */
public class DiagramCreator {

    static Second toTime(double time) {
        int days = (int) (time / 86400) + 1;
        int hours = (int) (time / 3600) - (days - 1) * 24;
        int minutes = (int) (time / 60) - hours * 60 - (days - 1) * 1440;
        int seconds = (int) time - minutes * 60 - hours * 3600 - (days - 1) * 86400;
        Second second = new Second(seconds, minutes, hours, days, 1, 2017); // month and year can not be zero
        return second;
    }

    public static void createDiagram(File directory, String fileTitle, String diagramTitle, Tensor time, Tensor values, boolean filter) throws Exception {
        createDiagram(directory, fileTitle, diagramTitle, time, values, 1.1, filter);
    }

    public static void createDiagram(File directory, String fileTitle, String diagramTitle, Tensor time, Tensor values) throws Exception {
        createDiagram(directory, fileTitle, diagramTitle, time, values, 1.1, false);
    }

    public static void createDiagram(File directory, String fileTitle, String diagramTitle, Tensor time, Tensor values, Double maxRange, boolean filter) throws Exception {
        final TimeSeriesCollection dataset = new TimeSeriesCollection();
        values = filter(values, time, 3, filter);
        for (int i = 0; i < values.length(); i++) {
            final TimeSeries series = new TimeSeries("time series " + i);
            for (int j = 0; j < time.length(); j++) {
                Second daytime = toTime(time.Get(j).number().doubleValue());

                series.add(daytime, values.Get(i, j).number().doubleValue());

            }
            dataset.addSeries(series);
        }

        JFreeChart timechart = ChartFactory.createTimeSeriesChart(diagramTitle, "Time", "Value", dataset, false, false, false);
        timechart.getXYPlot().getRangeAxis().setRange(0, maxRange);
        timechart.getPlot().setBackgroundPaint(Color.white);
        timechart.getXYPlot().setRangeGridlinePaint(Color.lightGray);
        timechart.getXYPlot().setDomainGridlinePaint(Color.lightGray);

        int width = 1200; /* Width of the image */
        int height = 900; /* Height of the image */
        File timeChart = new File(directory, fileTitle + ".png");
        ChartUtilities.saveChartAsPNG(timeChart, timechart, width, height);
        GlobalAssert.that(timeChart.exists() && !timeChart.isDirectory());
        System.out.println("exported " + fileTitle + ".png");
    }

    public static void createDiagram(File directory, String fileTitle, String diagramTitle, Tensor time, Tensor values, Double maxRange) throws Exception {
        createDiagram(directory, fileTitle, diagramTitle, time, values, maxRange, false);
    }

    /**
     * this function applies a standard moving average filter of length size to values //
     * if filter is set to true in AnalyzeAll
     *
     * @param values
     * @param size
     */
    public static Tensor filter(Tensor values, Tensor time, int size, boolean filter) {
        if (filter) {
            Tensor temp = Tensors.empty();
            temp = values;
            int offset = (int) (size / 2.0);
            for (int i = 0; i < values.length(); i++) {
                for (int j = size % 2 == 0 ? offset - 1 : offset; j < time.length() - offset; j++) {
                    double sum = 0;
                    for (int k = 0; k < size; k++) {
                        if (size % 2 == 0) {
                            sum += values.Get(i, j - offset + k + 1).number().doubleValue();
                        } else {
                            sum += values.Get(i, j - offset + k).number().doubleValue();
                        }
                    }
                    temp.set(RealScalar.of(sum / size), i, j);
                }
            }
            if (temp.Get(0).length() == values.Get(0).length() && //
                    temp.Get(1).length() == values.Get(1).length())
                return temp;
            else {
                GlobalAssert.that(false);
                return null;
            }
        } else {
            return values;
        }
    }

}
