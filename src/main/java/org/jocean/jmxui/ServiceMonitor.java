package org.jocean.jmxui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;

import org.jocean.j2se.zk.ZKAgent;
import org.jocean.zkoss.annotation.RowSource;
import org.jocean.zkoss.util.EventQueueForwarder;
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
import org.zkoss.zul.Row;

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
        
        public static class ASC implements Comparator<Row> {
            @Override
            public int compare(final Row o1, final Row o2) {
                return ((String)o1.getValue()).compareTo((String)o2.getValue());
            }
        }
        
        public static class DSC implements Comparator<Row> {
            @Override
            public int compare(final Row o1, final Row o2) {
                return ((String)o2.getValue()).compareTo((String)o1.getValue());
            }
        }
        
        @RowSource(name="主机", asc = ASC.class, dsc = DSC.class)
        private final String _host;
        
        @RowSource(name="用户", asc = ASC.class, dsc = DSC.class)
        private final String _user;
        
        @RowSource(name="服务", asc = ASC.class, dsc = DSC.class)
        private final String _service;
        
        @RowSource(name="构建号", asc = ASC.class, dsc = DSC.class)
        private String _buildNo;
        
        @RowSource(name="JMX")
        private final Button _btnShowJmx;
        
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
