package org.jocean.chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Timer;
import org.zkoss.zul.Window;

import h5chart.Axis;
import h5chart.H5Chart;
import h5chart.Multigraph;
import h5chart.Serie;

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class)
public class ChartComposer extends SelectorComposer<Window>{
	
    /**
     * 
     */
    private static final long serialVersionUID = 956865384217130725L;
    
    private static final Logger LOG = 
            LoggerFactory.getLogger(ChartComposer.class);
    
	public void doAfterCompose(final Window comp) throws Exception {
		super.doAfterCompose(comp);
		
		timer.addEventListener(Events.ON_TIMER, new EventListener<Event>() {
            @Override
            public void onEvent(Event event) throws Exception {
                updateMemoryChart();
            }});
		
		timer.start();
		
		multigraph = new Multigraph();
		multigraph.setLeft("10");
		multigraph.setTop("10");
		multigraph.setWidth("1000");
		multigraph.setHeight("200");
		multigraph.setAnimate(true);
        //t.setOrientation(Piramid.ORIENTATION_DOWN);
		multigraph.setMarks(true);
		multigraph.setShowTooltip(true);
		multigraph.setShowValues(true);
		multigraph.setGrid(false);
        multigraph.setLabelFont("bold 12px Arial");
        multigraph.setLabelColor("grey");
        
        multigraph.addLabel("-10 minutes");
        multigraph.addLabel("-9 minutes");
        multigraph.addLabel("-8 minutes");
        multigraph.addLabel("-7 minutes");
        multigraph.addLabel("-6 minutes");
        multigraph.addLabel("-5 minutes");
        multigraph.addLabel("-4 minutes");
        multigraph.addLabel("-3 minutes");
        multigraph.addLabel("-2 minutes");
        multigraph.addLabel("-1 minutes");
        multigraph.addLabel("current");
        
        
        serieUsedMemory = multigraph.addSerie(Multigraph.TYPE_LINE, "UsedMemory", Multigraph.FILL_VLINEAR, 5, true);
        
        axis = new Axis();
        
        axis.setLeft("0");
        axis.setTop("0");
        axis.setWidth("50");
        axis.setHeight("200");
        axis.setAnimate(false);
        axis.setShadow(false);
        axis.setLabelFont("6px Arial");
        axis.setLabelColor("grey");
        axis.setOrientation(Axis.ORIENTATION_VERTICAL);
        axis.setLineWidth(1);
        axis.setLabelsWidth(50);
        axis.setLabelsAngle(0);
        axis.setLabelsOnTick(true);
        axis.setTickPosition(Axis.TICK_POSITION_OVER);
        
        updateMemoryChart();
        
        h5chart.appendChild(multigraph);
        h5chart.appendChild(axis);
	}

    private void updateMemoryChart() {
        final long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        serieUsedMemory.add( (double)usedMem / 1024 / 1024);
        LOG.info("updateMemoryChart: {}",  (double)usedMem / 1024 / 1024);
        while (serieUsedMemory.size() > 11) {
            serieUsedMemory.remove(0);
        }
        
        axis.clearValues();
        final double max = maxOf(serieUsedMemory);
        axis.addValue(max, (int)max + "MB");
        axis.addValue(0d, "0MB");
        
        h5chart.invalidate();
    }

    private double maxOf(final Serie serieUsedMemory) {
        double max = 0;
        for (int idx = 0; idx < serieUsedMemory.size(); idx++) {
            if ( max < serieUsedMemory.getValue(idx) ) {
                max = serieUsedMemory.getValue(idx);
            }
        }
        return max;
    }

    @Wire
    private H5Chart h5chart;
    
    private Multigraph multigraph;
    
    private Serie serieUsedMemory;
    
    private Axis axis;
    
    @Wire
    private Timer timer;
}
