package org.jocean.jmxui;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.POST;

import org.jocean.http.Feature;
import org.jocean.http.rosa.SignalClient;
import org.jocean.jmxui.ListResponse.DomainInfo;
import org.jocean.jmxui.ListResponse.MBeanInfo;
import org.jocean.jmxui.ReadAttrResponse.AttrValue;
import org.jocean.jmxui.ServiceMonitor.ServiceInfo;
import org.jocean.zkoss.builder.GridBuilder;
import org.jocean.zkoss.model.SimpleTreeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zul.Caption;
import org.zkoss.zul.Columns;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Timer;
import org.zkoss.zul.Toolbarbutton;
import org.zkoss.zul.Tree;
import org.zkoss.zul.Treecell;
import org.zkoss.zul.Treeitem;
import org.zkoss.zul.TreeitemRenderer;
import org.zkoss.zul.Treerow;
import org.zkoss.zul.Window;

import rx.functions.Action1;

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class)
public class JmxComposer extends SelectorComposer<Window>{
	
    /**
     * 
     */
    private static final long serialVersionUID = -8824947431120569358L;

    private static final Logger LOG = 
        	LoggerFactory.getLogger(JmxComposer.class);
    
    // date format used to capture date time
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    
    private long getDateTime(String date) throws Exception {
        return sdf.parse(date).getTime();
    }
    
    class NodeTreeRenderer implements TreeitemRenderer<SimpleTreeModel.Node> {
        public void render(final Treeitem item, final SimpleTreeModel.Node node, int index) 
                throws Exception {
            item.setValue(node);
            item.appendChild( new Treerow() {
                private static final long serialVersionUID = 1L;
            {
                this.appendChild(new Treecell(node.getName()));
            }});
        }
    }
    
	public void doAfterCompose(final Window comp) throws Exception {
		super.doAfterCompose(comp);
		
        this.services.setRowRenderer(GridBuilder.buildRowRenderer(ServiceInfo.class));
        this.services.setSizedByContent(true);
        this.attrs.setRowRenderer(GridBuilder.buildRowRenderer(AttrValue.class));
        this.attrs.setSizedByContent(true);
        this.mbeans.setItemRenderer(new NodeTreeRenderer());
        
        this._serviceMonitor.monitorServices(new Action1<ServiceInfo[]>() {
            @Override
            public void call(final ServiceInfo[] serviceinfos) {
                try {
                    refreshServices(serviceinfos);
                } catch (Exception e) {
                }
            }}, 
            new Action1<ServiceInfo>() {
                @Override
                public void call(final ServiceInfo serviceinfo) {
                    try {
                        refreshJMX(serviceinfo);
                    } catch (URISyntaxException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }});
        this.mbeans.addEventListener(Events.ON_SELECT, refreshSelectedMBean());
        this.refresh.addEventListener(Events.ON_CLICK, refreshSelectedMBean());
        
        this.timer.addEventListener(Events.ON_TIMER, new EventListener<Event>() {
            @Override
            public void onEvent(Event event) throws Exception {
                updateCharts();
            }});
        
        timer.start();
	}

	private long queryUsedMemory(final String jolokiaUrl) {
        final JolokiaRequest req = new JolokiaRequest();
        req.setType("read");
        req.setMBean("java.lang:type=Memory");
        req.setAttribute("HeapMemoryUsage");
        req.setPath("used");
        
        try {
            final LongValueResponse resp = this._signalClient.<LongValueResponse>defineInteraction(req, 
                    Feature.ENABLE_LOGGING,
                    Feature.ENABLE_COMPRESSOR,
                    new SignalClient.UsingUri(new URI(jolokiaUrl)),
                    new SignalClient.UsingMethod(POST.class),
                    new SignalClient.DecodeResponseAs(LongValueResponse.class)
                    )
            .timeout(1, TimeUnit.SECONDS)
            .toBlocking().single();
            return resp.getValue();
        } catch (URISyntaxException e) {
            return 0;
        }
	}
	
    private void updateCharts() throws Exception {
        for (ServiceInfo info : this._serviceInfos) {
            final long usedMemory = queryUsedMemory(info.getJolokiaUrl());
            final int size = info.getUsedMemory().getDataCount("usedMemory");
            if (size >= 10) {
                info.getUsedMemory().addValue("usedMemory", getDateTime(sdf.format(new Date())), 
                        (int)( (double)usedMemory / 1024 / 1024), true);
            } else {
                info.getUsedMemory().addValue("usedMemory", getDateTime(sdf.format(new Date())), 
                       (int)( (double)usedMemory / 1024 / 1024));
            }
        }
    }

    private void refreshServices(final ServiceInfo[] infos) throws Exception {
        this.services.getChildren().clear();
        this.services.appendChild(new Columns() {
            private static final long serialVersionUID = 1L;
        {
            this.setSizable(true);
            GridBuilder.buildColumns(this, ServiceInfo.class);
        }});
        this.services.setModel( GridBuilder.buildListModel(ServiceInfo.class, 
                infos.length, 
                GridBuilder.fetchPageOf(infos),
                GridBuilder.fetchTotalSizeOf(infos),
                GridBuilder.sortModelOf(infos)));
        this._serviceInfos = infos;
        updateCharts();
    }
    
    private void refreshJMX(final ServiceInfo serviceinfo) throws URISyntaxException {
        this._jolokiauri = new URI(serviceinfo.getJolokiaUrl());
        this.servicetitle.setLabel("主机:" + serviceinfo.getHost() 
        + "  用户:" + serviceinfo.getUser() 
        + "  服务:" + serviceinfo.getService());
        final ListResponse resp = listMBeans();
        final DomainInfo[] domaininfos = resp.getDomains();
        this._model = new SimpleTreeModel(new SimpleTreeModel.Node(""));
        for (DomainInfo domain : domaininfos) {
            final SimpleTreeModel.Node child = 
                    this._model.getRoot().addChildIfAbsent(domain.getName());
            for (MBeanInfo mbeaninfo : domain.getMBeans()) {
                final SimpleTreeModel.Node mbeannode = 
                        child.addChildrenIfAbsent(buildPath(
                                mbeaninfo.getObjectName().getKeyPropertyListString()));
                mbeannode.setData(mbeaninfo);
            }
        }
        
        mbeans.setModel(this._model);
    }

    private EventListener<Event> refreshSelectedMBean() {
        return new EventListener<Event>() {
             
            @Override
            public void onEvent(final Event event) throws Exception {
                final SimpleTreeModel.Node node = currentSelectedNode();
                if ( null != node && null != node.getData()) {
                    LOG.info("select node:{}", (Object)node.getData());
                    displayMBeanInfo((MBeanInfo)node.getData());
                }
            }
        };
    }
	
	private void displayMBeanInfo(final MBeanInfo mbeaninfo) {
	    final ReadAttrResponse resp = queryAttrValue(mbeaninfo);
	    this.attrs.getChildren().clear();
	    final AttrValue[] attrvalues = resp.getValue();
	    this.attrs.appendChild(new Columns() {
            private static final long serialVersionUID = 1L;
        {
            this.setSizable(true);
            GridBuilder.buildColumns(this, AttrValue.class);
        }});
	    this.attrs.setModel( GridBuilder.buildListModel(AttrValue.class, 
	            attrvalues.length, 
                GridBuilder.fetchPageOf(attrvalues),
                GridBuilder.fetchTotalSizeOf(attrvalues)));
    }

    private ReadAttrResponse queryAttrValue(final MBeanInfo mbeaninfo) {
        final JolokiaRequest req = new JolokiaRequest();
        req.setType("read");
        req.setMBean(mbeaninfo.getObjectName().toString());
        
        final ReadAttrResponse resp = 
        this._signalClient.<ReadAttrResponse>defineInteraction(req, 
                Feature.ENABLE_LOGGING,
                Feature.ENABLE_COMPRESSOR,
                new SignalClient.UsingUri(this._jolokiauri),
                new SignalClient.UsingMethod(POST.class),
                new SignalClient.DecodeResponseAs(ReadAttrResponse.class)
                )
        .timeout(10, TimeUnit.SECONDS)
        .toBlocking().single();
        return resp;
    }
    
	private SimpleTreeModel.Node currentSelectedNode() {
        final Treeitem item = this.mbeans.getSelectedItem();

        if (null!=item) {
            final Object data = item.getValue();
        
            if ( data instanceof SimpleTreeModel.Node ) {
                return  (SimpleTreeModel.Node)data;
            }
        }
        return  null;
    }
	   
    private static String[] buildPath(final String rawpath) {
        return rawpath.split(",");
    }
    
    private ListResponse listMBeans() {
        final JolokiaRequest req = new JolokiaRequest();
        req.setType("list");
        
        final ListResponse resp = 
        this._signalClient.<ListResponse>defineInteraction(req, 
                Feature.ENABLE_LOGGING,
                Feature.ENABLE_COMPRESSOR,
                new SignalClient.UsingUri(_jolokiauri),
                new SignalClient.UsingMethod(POST.class),
                new SignalClient.DecodeResponseAs(ListResponse.class)
                )
        .timeout(10, TimeUnit.SECONDS)
        .toBlocking().single();
        return resp;
    }

    @Wire
    private Grid    services;
    
    private ServiceInfo[] _serviceInfos;
    
    @Wire
    private Caption servicetitle;
    
    @Wire
    private Tree    mbeans;
    
    private SimpleTreeModel _model;

    @Wire
    private Grid            attrs;
    
    @Wire
    private Toolbarbutton   refresh;
    
    @Wire
    private Caption         status;
    
    @WireVariable("servicemonitor") 
    private ServiceMonitor _serviceMonitor;
    
    private URI _jolokiauri;
    
    @WireVariable("signalClient") 
    private SignalClient _signalClient;
    
    @Wire
    private Timer timer;
}
