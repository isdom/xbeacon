package org.jocean.jmxui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;

import org.jocean.j2se.zk.ZKAgent;
import org.jocean.zkoss.annotation.RowSource;
import org.jocean.zkoss.util.EventQueueForwarder;
import org.ngi.zhighcharts.SimpleExtXYModel;
import org.ngi.zhighcharts.ZHighCharts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueue;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.MouseEvent;
import org.zkoss.zk.ui.util.DesktopCleanup;
import org.zkoss.zul.Button;

import com.google.common.collect.Maps;

import rx.functions.Action1;

public class ServiceMonitor {
    private static final ServiceInfo[] EMPTY_INFO = new ServiceInfo[0];
    public static class ServiceInfo {
        
        public ServiceInfo(final String host, final String user, final String service,
                final Action1<ServiceInfo> onShowJmx) {
            this._host = host;
            this._user = user;
            this._service = service;
            this._btnShowJmx = new Button("控制台");
            this._btnShowJmx.addEventListener(Events.ON_CLICK, 
                new EventListener<MouseEvent>() {
                    @Override
                    public void onEvent(MouseEvent event) throws Exception {
                        onShowJmx.call(ServiceInfo.this);
                    }});
            this._chartMemory = new ZHighCharts();
            this._chartMemory.setWidth("240px");
            this._chartMemory.setHeight("80px");
            
            this._chartMemory.setOptions("{" +
                    "marginRight: 0," +
                "}");
            this._chartMemory.setTitleOptions("{" +
                "text: null" +
            "}");
            this._chartMemory.setType("spline"); // spline/line
            this._chartMemory.setxAxisOptions("{ " +
                    "labels: {" + 
                        "enabled: false" +
                    "}," +
                    "type: 'datetime'," +
                    "tickPixelInterval: 40" +
                "}");
            this._chartMemory.setyAxisOptions("{" +
                    "plotLines: [" +
                        "{" +
                            "value: 0," +
                            "width: 1," +
                            "color: '#808080'" +
                        "}" +
                    "]" +
                "}");
            this._chartMemory.setYAxisTitle(null);
            this._chartMemory.setTooltipFormatter("function formatTooltip(obj){" +
                    "return '<b>'+ obj.series.name +'</b><br/>" +
                    "'+Highcharts.dateFormat('%Y-%m-%d %H:%M:%S', obj.x) +'<br/>" +
                    "'+Highcharts.numberFormat(obj.y, 2);" +
                "}");
            this._chartMemory.setPlotOptions("{" +
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
            this._chartMemory.setExporting("{" +
                    "enabled: false " +
                "}");
            this._chartMemory.setLegend("{" +
                    "enabled: false " +
                "}");
        
            this._chartMemory.setModel(this._usedMemoryModel);
        }
        
        /**
         * @return the host
         */
        public String getHost() {
            return _host;
        }

        /**
         * @return the user
         */
        public String getUser() {
            return _user;
        }

        /**
         * @return the service
         */
        public String getService() {
            return _service;
        }

        /**
         * @return the _jolokiaUrl
         */
        public String getJolokiaUrl() {
            return _jolokiaUrl;
        }

        /**
         * @param jolokiaUrl the _jolokiaUrl to set
         */
        public void setJolokiaUrl(final String jolokiaUrl) {
            this._jolokiaUrl = jolokiaUrl;
        }

        public String getBuildNo() {
            return this._buildNo;
        }
        
        public void setBuildNo(final String buildno) {
            this._buildNo = buildno;
        }
        
        /**
         * @return the _chartMemory
         */
        public ZHighCharts getChartMemory() {
            return _chartMemory;
        }

        /**
         * @return the usedMemory
         */
        public SimpleExtXYModel getUsedMemory() {
            return this._usedMemoryModel;
        }

        public static class HOST_ASC implements Comparator<ServiceInfo>  {
            @Override
            public int compare(final ServiceInfo o1, final ServiceInfo o2) {
                return o1._host.compareTo(o2._host);
            }
        }
        
        public static class HOST_DSC implements Comparator<ServiceInfo> {
            @Override
            public int compare(final ServiceInfo o1, final ServiceInfo o2) {
                return o2._host.compareTo(o1._host);
            }
        }
        
        @RowSource(name="主机", asc = HOST_ASC.class, dsc = HOST_DSC.class)
        private final String _host;
        
        public static class USER_ASC implements Comparator<ServiceInfo>  {
            @Override
            public int compare(final ServiceInfo o1, final ServiceInfo o2) {
                return o1._user.compareTo(o2._user);
            }
        }
        
        public static class USER_DSC implements Comparator<ServiceInfo> {
            @Override
            public int compare(final ServiceInfo o1, final ServiceInfo o2) {
                return o2._user.compareTo(o1._user);
            }
        }
        
        @RowSource(name="用户", asc = USER_ASC.class, dsc = USER_DSC.class)
        private final String _user;
        
        public static class SRV_ASC implements Comparator<ServiceInfo>  {
            @Override
            public int compare(final ServiceInfo o1, final ServiceInfo o2) {
                return o1._service.compareTo(o2._service);
            }
        }
        
        public static class SRV_DSC implements Comparator<ServiceInfo> {
            @Override
            public int compare(final ServiceInfo o1, final ServiceInfo o2) {
                return o2._service.compareTo(o1._service);
            }
        }
        
        @RowSource(name="服务", asc = SRV_ASC.class, dsc = SRV_DSC.class)
        private final String _service;
        
        @RowSource(name="构建号")
        private String _buildNo;
        
        @RowSource(name="JMX")
        private final Button _btnShowJmx;
        
        @RowSource(name="使用内存(MB)")
        private final ZHighCharts _chartMemory;
        
        private SimpleExtXYModel _usedMemoryModel = new SimpleExtXYModel();
        
        private String _jolokiaUrl;
    }
    
    private static final Logger LOG = 
            LoggerFactory.getLogger(ServiceMonitor.class);

    public ServiceMonitor(final ZKAgent zkagent) {
        this._zkagent = zkagent;
        this._rootPath = zkagent.root();
    }
    
    public void setRoot(final String rootPath) {
        this._rootPath = rootPath;
    }
    
    public void monitorServices(final Action1<ServiceInfo[]> onServiceChanged,
            final Action1<ServiceInfo> onShowJmx) throws Exception {
        final Map<String, ServiceInfo> services = Maps.newHashMap();
        final EventQueueForwarder<ZKAgent.Listener> eqf = 
                new EventQueueForwarder<>(ZKAgent.Listener.class, this._eventqueue);
        
        eqf.subscribe(new ZKAgent.Listener() {

            @Override
            public void onAdded(final String absolutepath, final byte[] data) throws Exception {
                if (!isServiceStatusPath(absolutepath)) {
                    return;
                }
                final String path = absolute2relative(absolutepath);
                final ServiceInfo service = buildServiceInfo(path, onShowJmx);
                if ( null != service ) {
                    updateServiceInfo(service, data);
                    services.put(path, service);
                    onServiceChanged.call(services.values().toArray(EMPTY_INFO));
                }
            }

            @Override
            public void onUpdated(String absolutepath, byte[] data) throws Exception {
                if (!isServiceStatusPath(absolutepath)) {
                    return;
                }
                final String path = absolute2relative(absolutepath);
                final ServiceInfo service = services.get(path);
                if (null != service) {
                    updateServiceInfo(service, data);
                    onServiceChanged.call(services.values().toArray(EMPTY_INFO));
                }
            }

            @Override
            public void onRemoved(String absolutepath) throws Exception {
                if (!isServiceStatusPath(absolutepath)) {
                    return;
                }
                final String path = absolute2relative(absolutepath);
                final ServiceInfo service = services.remove(path);
                if (null != service) {
                    onServiceChanged.call(services.values().toArray(EMPTY_INFO));
                }
            }});
        final Runnable stop = this._zkagent.addListener(eqf.subject());
        final Desktop desktop = Executions.getCurrent().getDesktop();
        desktop.addListener(new DesktopCleanup() {
            @Override
            public void cleanup(final Desktop desktop) throws Exception {
                LOG.info("cleanup for desktop {}", desktop);
                stop.run();
            }});
    }
    
    protected ServiceInfo buildServiceInfo(final String path,final Action1<ServiceInfo> onShowJmx) {
        final String[] pieces = path.split("\\.");
        if (pieces.length >= 4) {
            return new ServiceInfo(pieces[1], pieces[2], pieces[3], onShowJmx);
        } else {
            return null;
        }
    }

    /**
     * @param _webapp the _webapp to set
     */
    public void setWebapp(final WebApp webapp) {
        this._webapp = webapp;
    }

    public void start() throws Exception {
        this._eventqueue = EventQueues.lookup("service", this._webapp, true);
    }
    
    public void stop() {
        EventQueues.remove("service", this._webapp);
    }

    private String absolute2relative(final String rawpath) {
        return rawpath.substring(rootPathSize() + 1);
    }

    private int rootPathSize() {
        return this._rootPath.length() - ( this._rootPath.endsWith("/") ? 1 : 0);
    }

    private boolean isServiceStatusPath(final String absolutepath) {
        return absolutepath.startsWith(this._rootPath + "/");
    }

    private void updateServiceInfo(final ServiceInfo service, final byte[] data)
            throws IOException {
        final Properties prop = new Properties();
        prop.load(new ByteArrayInputStream(data));
        service.setBuildNo(prop.getProperty("build.no"));
        service.setJolokiaUrl(prop.getProperty("jolokia.url"));
    }

    private ZKAgent _zkagent;
    private WebApp _webapp;
    private String _rootPath;
    private EventQueue<Event> _eventqueue;
}
