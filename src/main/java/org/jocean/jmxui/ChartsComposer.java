package org.jocean.jmxui;

import java.util.List;
import java.util.Map;

import org.jocean.idiom.Triple;
import org.jocean.jmxui.ServiceMonitor.Indicator;
import org.jocean.jmxui.ServiceMonitor.InitStatus;
import org.jocean.jmxui.ServiceMonitor.ServiceInfo;
import org.jocean.jmxui.ServiceMonitor.UpdateStatus;
import org.ngi.zhighcharts.SimpleExtXYModel;
import org.ngi.zhighcharts.ZHighCharts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zul.Window;

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class) 
public class ChartsComposer extends SelectorComposer<Window>{
	

    /**
     * 
     */
    private static final long serialVersionUID = -986472541771188129L;

    @SuppressWarnings("unused")
    private static final Logger LOG = 
        	LoggerFactory.getLogger(ChartsComposer.class);
    
    
	public void doAfterCompose(final Window comp) throws Exception {
		super.doAfterCompose(comp);
		
		this.chartMemory.setOptions("{" +
                "marginRight: 10" +
            "}");
        chartMemory.setTitle("Used Memory");
        chartMemory.setType("spline");
        chartMemory.setxAxisOptions("{ " +
                    "type: 'datetime'," +
                    "tickPixelInterval: 150" +
                "}");
        chartMemory.setyAxisOptions("{" +
                    "plotLines: [" +
                        "{" +
                            "value: 0," +
                            "width: 1," +
                            "color: '#808080'" +
                        "}" +
                    "]" +
                "}");
        chartMemory.setYAxisTitle("Memory Size");
        chartMemory.setTooltipFormatter("function formatTooltip(obj){" +
                    "return '<b>'+ obj.series.name +'</b><br/>" +
                    "'+Highcharts.dateFormat('%Y-%m-%d %H:%M:%S', obj.x) +'<br/>" +
                    "'+Highcharts.numberFormat(obj.y, 2);" +
                "}");
        chartMemory.setPlotOptions("{" +
                    "series: {" +
                        "marker: {" +
                            "radius: 2" +
                        "}," +
                        "allowPointSelect: true," +
                        "cursor: 'pointer'," +
                        "lineWidth: 1," +
                        "dataLabels: {" +
                            "formatter: function (){return this.y;}," + 
                            "enabled: true," +
                            "style: {" +
                                "fontSize: '8px'" +
                            "}" +
                        "}," +
                        "showInLegend: true" +
                    "}" +
                "}");
        chartMemory.setLegend("{" +
                    "enabled: true " +
                "}");
    
		this.chartMemory.setModel(this.modelMemory);
		
        this._serviceMonitor.subscribeServiceStatus(10,
            new InitStatus() {
                @Override
                public void call(final Map<ServiceInfo, Map<String, Indicator[]>> status) {
                    for (Map.Entry<ServiceInfo, Map<String, Indicator[]>> entry : status.entrySet()) {
                        final ServiceInfo info = entry.getKey();
                        final Indicator[] inds = entry.getValue().get("usedMemory");
                        if (null != inds) {
                            for (Indicator ind : inds) {
                                addUsedMemoryInd(info.getId(), ind.getTimestamp(), (Long)ind.getValue());
                            }
                        }
                    }
                }},
            new UpdateStatus() {

                @Override
                public void onServiceAdded(final ServiceInfo info) {
                }

                @Override
                public void onServiceUpdated(final ServiceInfo info) {
                    //  TODO
                }

                @Override
                public void onServiceRemoved(final String id) {
                    modelMemory.removeSeries(id);
                }

                @Override
                public void onIndicator(final List<Triple<ServiceInfo, String, Indicator>> inds) {
                    for (Triple<ServiceInfo, String, Indicator> ind : inds) {
                        if (null != ind.third) {
                            final String id = ind.first.getId();
                            final long timestamp = ind.third.getTimestamp();
                            final long value = ind.third.getValue();
                            
                            addUsedMemoryInd(id, timestamp, value);
                        }
                    }
                }});
	}

    private void addUsedMemoryInd(
            final String id, 
            final long timestamp,
            final long value) {
        final int size = this.modelMemory.getDataCount(id);
        if (size >= 10) {
            modelMemory.addValue(id, timestamp, 
                    (int)( (double)value / 1024 / 1024), true);
        } else {
            modelMemory.addValue(id, timestamp, 
                    (int)( (double)value / 1024 / 1024));
        }
    }

    @Wire
    private ZHighCharts chartMemory;
    
    private SimpleExtXYModel modelMemory = new SimpleExtXYModel();
    
    @WireVariable("servicemonitor") 
    private ServiceMonitor _serviceMonitor;
}
