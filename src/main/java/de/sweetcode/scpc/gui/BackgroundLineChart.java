package de.sweetcode.scpc.gui;

import de.sweetcode.scpc.data.CaptureSession;
import de.sweetcode.scpc.data.DataPoint;
import javafx.collections.ObservableList;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Polygon;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class BackgroundLineChart extends LineChart<Number, Number> {

    private final CaptureSession captureSession;
    private BackgroundType backgroundType = BackgroundType.NONE;

    public BackgroundLineChart(CaptureSession captureSession, Axis<Number> xAxis, Axis<Number> yAxis, ObservableList<Series<Number, Number>> data) {
        super(xAxis, yAxis, data);
        this.captureSession = captureSession;
    }

    public void setBackgroundType(BackgroundType backgroundType) {
        this.backgroundType = backgroundType;
    }

    public void redraw() {
        this.layoutPlotChildren();
    }

    @Override
    protected void layoutPlotChildren() {
        super.layoutPlotChildren();

        //NOTE: We remove the old Polygons os that when we redraw, we only draw the new ones and the old
        // Polygons disappear.
        this.getPlotChildren().removeIf(e -> e instanceof Polygon);

        if(this.backgroundType == BackgroundType.NONE) return;

        Polygon polygon = new Polygon();
        final double ground = this.getYAxis().getDisplayPosition(0);

        boolean inMiddle = false;
        List<Double> bottom = new LinkedList<>();

        Series series = this.getData().get(0);
        ObservableList<Data<Number,Number>> data = series.getData();

        for(int i = 0; i < this.getData().get(0).getData().size(); i++) {

            //
            DataPoint pre = (i == 0 ? null : captureSession.get(data.get(i).getXValue().intValue()));
            DataPoint current = captureSession.get(data.get(i).getXValue().intValue());
            DataPoint post = (i == data.size() - 1 ? null : captureSession.get(data.get(i + 1).getXValue().intValue()));

            double highestPreValue = (pre == null ? ground : highestYValue((i - 1)));
            double highestCurrentValue = highestYValue(i);
            double highestPostValue = (post == null ? ground : highestYValue((i + 1)));

            //--
            double preX = (i == 0 ? ground : this.getXAxis().getDisplayPosition(data.get(i-1).getXValue()));
            double preY = (i == 0 ? ground : this.getYAxis().getDisplayPosition(highestPreValue));

            double currX = (this.getXAxis().getDisplayPosition(current.getData(DataPoint.Types.VEHICLES_AI).getXValue()));
            double currY = (this.getYAxis().getDisplayPosition(highestCurrentValue));

            double postX = (post == null ? ground : this.getXAxis().getDisplayPosition(data.get(i+1).getXValue()));
            double postY = (post == null ? ground : this.getYAxis().getDisplayPosition(highestPostValue));

            //--- Start
            if(polygon.getPoints().isEmpty() && current.getGameState().getBackground() != null) {
                polygon.getPoints().addAll(
                        currX,ground, currX,currY
                );
                inMiddle = true;
            }
            //--- Middle
            else if(inMiddle) {
                inMiddle = (post != null && current.getGameState() == post.getGameState());

                if(inMiddle) {
                    polygon.getPoints().addAll(
                            preX,preY, currX, currY,
                            currX, currY, postX, postY
                    );
                    bottom.add(preX);
                    bottom.add(ground);
                    bottom.add(currX);
                    bottom.add(ground);
                    bottom.add(currX);
                    bottom.add(ground);
                    bottom.add(postX);
                    bottom.add(ground);
                } else {

                    polygon.getPoints().addAll(
                            preX,preY, currX, currY,
                            currX,currY, currX,ground,
                            currX,ground,preX,ground
                    );

                    polygon.getPoints().addAll(bottom);
                    getPlotChildren().add(polygon);
                    polygon.toBack();

                    polygon.setOpacity(0.2D);

                    switch (this.backgroundType) {
                        case COLOUR: polygon.setFill(this.captureSession.get(i).getGameState().getBackgroundGradient()); break;
                        case IMAGE: polygon.setFill(new ImagePattern(current.getGameState().getBackground())); break;
                    }


                    polygon = new Polygon();
                    bottom = new LinkedList<>();
                }
            }

        }

    }

    private double highestYValue(int index) {
        Iterator<Series<Number, Number>> iterator = this.getData().iterator();
        double max = Double.NEGATIVE_INFINITY;
        while (iterator.hasNext()) {
            Data<Number, Number> numberData = iterator.next().getData().get(index);
            if (numberData.getNode().isVisible()) {
                max = Math.max(numberData.getYValue().doubleValue(), max);
            }
        }
        return max;
    }

    public enum BackgroundType {

        NONE,
        COLOUR,
        IMAGE;

    }

}