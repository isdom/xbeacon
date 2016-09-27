package org.jocean.zchart;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.ngi.zhighcharts.SimpleExtXYModel;
import org.ngi.zhighcharts.ZHighCharts;
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

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class)
public class ZChartComposer extends SelectorComposer<Window>{
	
    /**
     * 
     */
    private static final long serialVersionUID = 956865384217130725L;
    
    private static final Logger LOG = 
            LoggerFactory.getLogger(ZChartComposer.class);
    
    //Spline updating each second
    @Wire
    private ZHighCharts chartComp21;
    
    private SimpleExtXYModel dataChartModel21 = new SimpleExtXYModel();

    // date format used to capture date time
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    
    @Override
    public void doAfterCompose(final Window comp) throws Exception {
        super.doAfterCompose(comp);

        //================================================================================
        // Spline updating each second
        //================================================================================
        
        setupChart();
        
        //Adding some random data to the model

//        for (int i = -19; i <= 0; i++){
//            dataChartModel21.addValue("Random data",getDateTime(sdf.format(new Date())) + i *1000, Math.random());
//        }           
        
        timer.addEventListener(Events.ON_TIMER, new EventListener<Event>() {
            @Override
            public void onEvent(final Event event) throws Exception {
                updateMemoryChart();
            }
        });

        dataChartModel21.setShift(true);
        updateMemoryChart();
        
        timer.start();
    }

    private void setupChart() {
        chartComp21.setWidth("240px");
        chartComp21.setHeight("80px");
        chartComp21.setOptions("{" +
                    "marginRight: 0," +
//                    "events: {" +
//                        "load: function() {" +
//                            "var series = this.series[0];" +
//                            "setInterval(function() {" +
//                                "var x = (new Date()).getTime(),y = Math.random();" +
//                                "series.addPoint([x, y], true, true);" +
//                                "}," +
//                            " 1000);" +
//                        "}" +
//                    "}" +
                "}");
        chartComp21.setTitleOptions("{" +
                "text: null" +
            "}");
//        chartComp21.setTitle("Live random data");
        chartComp21.setType("spline"); // spline/line
        chartComp21.setxAxisOptions("{ " +
                    "labels: {" + 
                        "enabled: false" +
                    "}," +
                    "type: 'datetime'," +
                    "tickPixelInterval: 40" +
                "}");
        chartComp21.setyAxisOptions("{" +
                    "plotLines: [" +
                        "{" +
                            "value: 0," +
                            "width: 1," +
                            "color: '#808080'" +
                        "}" +
                    "]" +
                "}");
        chartComp21.setYAxisTitle(null);
        chartComp21.setTooltipFormatter("function formatTooltip(obj){" +
                    "return '<b>'+ obj.series.name +'</b><br/>" +
                    "'+Highcharts.dateFormat('%Y-%m-%d %H:%M:%S', obj.x) +'<br/>" +
                    "'+Highcharts.numberFormat(obj.y, 2);" +
                "}");
        chartComp21.setPlotOptions("{" +
                    "series: {" + // pie
                        "allowPointSelect: true," +
                        "cursor: 'pointer'," +
                        "lineWidth: 1," +
                        "dataLabels: {" +
                            "formatter: function (){return this.y;}," + 
                            "enabled: true," +
                            "style: {" +
                                "fontSize: '6px'" +
                            "}" +
                        "}," +
                        "showInLegend: true" +
                    "}" +
                "}");
        chartComp21.setExporting("{" +
                    "enabled: false " +
                "}");
        chartComp21.setLegend("{" +
                    "enabled: false " +
                "}");
        
        chartComp21.setModel(dataChartModel21);
    }
    
    /**
     * internal method to convert date&amp;time from string to epoch milliseconds
     * 
     * @param date
     * @return
     * @throws Exception
     */
    private long getDateTime(String date) throws Exception {
        return sdf.parse(date).getTime();
    }

    private void updateMemoryChart() throws Exception {
        final long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        final int size = dataChartModel21.getDataCount("Random data");
        LOG.info("Random data: {}", size);
        if (size >= 8) {
            dataChartModel21.addValue("Random data", getDateTime(sdf.format(new Date())), 
                    (int)( (double)usedMem / 1024 / 1024), true);
        } else {
            dataChartModel21.addValue("Random data", getDateTime(sdf.format(new Date())), 
                   (int)( (double)usedMem / 1024 / 1024));
        }
    }

    @Wire
    private Timer timer;
}
