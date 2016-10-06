package org.jocean.xbeacon.jmxui;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.POST;

import org.jocean.http.Feature;
import org.jocean.http.rosa.SignalClient;
import org.jocean.idiom.Triple;
import org.jocean.xbeacon.jmxui.ServiceMonitor.Indicator;
import org.jocean.xbeacon.jmxui.ServiceMonitor.InitStatus;
import org.jocean.xbeacon.jmxui.ServiceMonitor.ServiceInfo;
import org.jocean.xbeacon.jmxui.ServiceMonitor.UpdateStatus;
import org.jocean.xbeacon.jmxui.bean.ExecResponse;
import org.jocean.xbeacon.jmxui.bean.JolokiaRequest;
import org.jocean.xbeacon.jmxui.bean.ListResponse;
import org.jocean.xbeacon.jmxui.bean.ReadAttrResponse;
import org.jocean.xbeacon.jmxui.bean.ListResponse.ArgInfo;
import org.jocean.xbeacon.jmxui.bean.ListResponse.DomainInfo;
import org.jocean.xbeacon.jmxui.bean.ListResponse.MBeanInfo;
import org.jocean.xbeacon.jmxui.bean.ListResponse.OperationInfo;
import org.jocean.zkoss.annotation.RowSource;
import org.jocean.zkoss.builder.GridBuilder;
import org.jocean.zkoss.model.SimpleTreeModel;
import org.jocean.zkoss.ui.JsonUI;
import org.jocean.zkoss.util.EventQueueForwarder;
import org.ngi.zhighcharts.SimpleExtXYModel;
import org.ngi.zhighcharts.ZHighCharts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueue;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.MouseEvent;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zul.Button;
import org.zkoss.zul.Caption;
import org.zkoss.zul.Center;
import org.zkoss.zul.Columns;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Intbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Progressmeter;
import org.zkoss.zul.Toolbarbutton;
import org.zkoss.zul.Tree;
import org.zkoss.zul.Treecell;
import org.zkoss.zul.Treeitem;
import org.zkoss.zul.TreeitemRenderer;
import org.zkoss.zul.Treerow;
import org.zkoss.zul.Window;

import com.alibaba.fastjson.JSONArray;

import rx.Observer;
import rx.functions.Action0;
import rx.functions.Action1;

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class)
public class JmxComposer extends SelectorComposer<Window>{
	
    private static final ServiceData[] EMPTY_SRV = new ServiceData[0];

    /**
     * 
     */
    private static final long serialVersionUID = -8824947431120569358L;

    private static final Logger LOG = 
        	LoggerFactory.getLogger(JmxComposer.class);
    
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
		
        this.services.setRowRenderer(GridBuilder.buildRowRenderer(ServiceData.class));
        this.services.setSizedByContent(true);
        this.mbeans.setItemRenderer(new NodeTreeRenderer());
        
        this.mbeans.addEventListener(Events.ON_SELECT, showSelectedMBean());
        this.refresh.addEventListener(Events.ON_CLICK, showSelectedMBean());
        
        this._eventqueue = EventQueues.lookup("callback", EventQueues.SESSION, true);
        
        this.apply.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(Event event) throws Exception {
                indHistorySize = indlen.getValue();
                subscribeServiceData();
            }});
        subscribeServiceData();
	}

    private void subscribeServiceData() {
        if (null != this._unsubscribeServiceStatus) {
            this._unsubscribeServiceStatus.call();
        }
        this._serviceDatas.clear();
        this.services.getChildren().clear();
        this._unsubscribeServiceStatus = this._serviceMonitor.subscribeServiceStatus(
            indHistorySize,
            new InitStatus() {
                @Override
                public void call(final Map<ServiceInfo, Map<String, Indicator[]>> status) {
                    for (Map.Entry<ServiceInfo, Map<String, Indicator[]>> entry : status.entrySet()) {
                        final ServiceInfo info = entry.getKey();
                        final ServiceData data = addServiceInfo(info);
                        final Indicator[] inds = entry.getValue().get("usedMemory");
                        if (null != inds) {
                            for (Indicator ind : inds) {
                                data.addUsedMemoryInd(ind);
                            }
                        }
                        _serviceDatas.add(data);
                    }
                    
                    updateServicesModel(_serviceDatas.toArray(EMPTY_SRV));
                }},
            new UpdateStatus() {

                @Override
                public void onServiceAdded(final ServiceInfo info) {
                    final ServiceData data = addServiceInfo(info);
                    _serviceDatas.add(data);
                    updateServicesModel(_serviceDatas.toArray(EMPTY_SRV));
                }

                @Override
                public void onServiceUpdated(final ServiceInfo info) {
                    //  TODO
                }

                @Override
                public void onServiceRemoved(final String id) {
                    final Iterator<ServiceData> iter = _serviceDatas.iterator();
                    while (iter.hasNext()) {
                        final ServiceData data = iter.next();
                        if (id.equals(data._id)) {
                            iter.remove();
                            break;
                        }
                    }
                    updateServicesModel(_serviceDatas.toArray(EMPTY_SRV));
                }

                @Override
                public void onIndicator(final List<Triple<ServiceInfo, String, Indicator>> inds) {
                    for (Triple<ServiceInfo, String, Indicator> ind : inds) {
                        if (null != ind.third) {
                            final ServiceData data = findServiceData(ind.first.getId());
                            if (null != data) {
                                data.addUsedMemoryInd(ind.third);
                            }
                        }
                    }
                }});
    }

    private void queryServiceMBeans(final ServiceData data) throws URISyntaxException {
        this._jolokiauri = new URI(data._jolokiaUrl);
        this.servicetitle.setLabel("主机:" + data._host
        + "  用户:" + data._user
        + "  服务:" + data._service);
        listMBeans(new Action1<ListResponse>() {
            @Override
            public void call(final ListResponse resp) {
                if ( 200 == resp.getStatus()) {
                    showServiceMBeans(resp);
                }
            }});
    }

    private void showServiceMBeans(final ListResponse resp) {
        final DomainInfo[] domains = resp.getDomains();
        this._model = new SimpleTreeModel(new SimpleTreeModel.Node(""));
        for (DomainInfo domain : domains) {
            final SimpleTreeModel.Node child = 
                this._model.getRoot().addChildIfAbsent(domain.getName());
            for (MBeanInfo mbeaninfo : domain.getMBeans()) {
                final SimpleTreeModel.Node mbeannode = 
                        child.addChildrenIfAbsent(buildPath(
                                mbeaninfo.getObjectName().getKeyPropertyListString()));
                mbeannode.setData(mbeaninfo);
            }
        }
        
        this.mbeans.setModel(this._model);
        this.status.getChildren().clear();
    }

    private EventListener<Event> showSelectedMBean() {
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
	    showMBeanOperations(mbeaninfo, new Action1<OperationInfo>() {
            @Override
            public void call(final OperationInfo op) {
                lauchOperation(mbeaninfo, op);
            }});
	    queryAttrValue(mbeaninfo, new Action1<ReadAttrResponse>() {
            @Override
            public void call(final ReadAttrResponse resp) {
                if (200 == resp.getStatus()) {
                    showMBeanAttributes(resp);
                }
            }});
    }

    private void buildArgsGrid(final OperationInfo op, final Grid grid) {
        grid.setRowRenderer(GridBuilder.buildRowRenderer(ArgInfo.class));
        grid.setSizedByContent(true);
        grid.appendChild(new Columns() {
            private static final long serialVersionUID = 1L;
        {
            this.setSizable(true);
            GridBuilder.buildColumns(this, ArgInfo.class);
        }});
        grid.setModel( GridBuilder.buildListModel(ArgInfo.class, 
                op.getArgs().length, 
                GridBuilder.fetchPageOf(op.getArgs()),
                GridBuilder.fetchTotalSizeOf(op.getArgs())));
    }
    
    private void lauchOperation(final MBeanInfo mbeaninfo, final OperationInfo op) {
        final Window dialog = new Window("invoke " + op.genNameWithSignature(), "normal", true);
        dialog.setWidth("400px");
        dialog.setHeight("550px");
        dialog.setSizable(true);
        dialog.setPage(this.getPage());
        
        dialog.appendChild(new Label("输入执行 " + op.getName() + " 所需的参数"));
        final Grid gridArgs = new Grid();
        dialog.appendChild(gridArgs);
        buildArgsGrid(op, gridArgs);
        
        final Button btnExec = new Button("执行") {
            private static final long serialVersionUID = 1L;
            {
                this.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
                    @Override
                    public void onEvent(Event event) throws Exception {
                        final JSONArray args = op.genArgArray();
                        Messagebox.show("invoke " + op.getName() + " with args(" + args + ")?", 
                                "exec operation", Messagebox.OK | Messagebox.CANCEL, Messagebox.QUESTION,
                                new EventListener<Event>() {
                                    @Override
                                    public void onEvent(Event event)
                                            throws Exception {
                                        if (Messagebox.ON_OK.equals(event.getName())){
                                            invokeOperation(mbeaninfo, op, args);
                                        }
                                    }});
                        
                    }});
            }
            };
        dialog.appendChild(btnExec);
        dialog.doModal();
    }
    
    private void invokeOperation(final MBeanInfo mbean, final OperationInfo op, final JSONArray args) {
        final JolokiaRequest req = new JolokiaRequest();
        req.setType("exec");
        req.setMBean(mbean.getObjectName().toString());
        req.setOperation(op.genNameWithSignature());
        req.setArguments(args);
        
        final ExecResponse resp =
            this._signalClient.<ExecResponse>defineInteraction(req, 
                    Feature.ENABLE_LOGGING,
                    Feature.ENABLE_COMPRESSOR,
                    new SignalClient.UsingUri(_jolokiauri),
                    new SignalClient.UsingMethod(POST.class),
                    new SignalClient.DecodeResponseAs(ExecResponse.class)
                    )
            .timeout(1, TimeUnit.SECONDS)
            .toBlocking().single();
        if (200 == resp.getStatus()) {
            Messagebox.show("invoke " + op.getName() + " success, return: " + resp.getValue(), 
                    "exec operation result", Messagebox.OK, Messagebox.INFORMATION);
        } else {
            Messagebox.show("invoke " + op.getName() + " failed, status code is " + resp.getStatus(), 
                    "exec operation result", Messagebox.OK, Messagebox.ERROR);
        }
    }
    
    private void showMBeanOperations(final MBeanInfo mbeaninfo, final Action1<OperationInfo> invokeOperation) {
        this.ops.getChildren().clear();
        
        final OperationInfo[] infos = mbeaninfo.getOperations();
        
        if (null != infos) {
            for (OperationInfo info : infos) {
                info.setInvoker(invokeOperation);
            }
            
            final Grid grid = new Grid();
            this.ops.appendChild(grid);
            grid.setRowRenderer(GridBuilder.buildRowRenderer(OperationInfo.class));
            grid.setSizedByContent(true);
            grid.appendChild(new Columns() {
                private static final long serialVersionUID = 1L;
            {
                this.setSizable(true);
                GridBuilder.buildColumns(this, OperationInfo.class);
            }});
            grid.setModel( GridBuilder.buildListModel(OperationInfo.class, 
                    infos.length, 
                    GridBuilder.fetchPageOf(infos),
                    GridBuilder.fetchTotalSizeOf(infos)));
        }
    }

    private void showMBeanAttributes(final ReadAttrResponse resp) {
        this.attrs.getChildren().clear();
        this.attrs.appendChild((Component)JsonUI.buildUI(resp.getValue()));
        this.status.getChildren().clear();
    }

    private void queryAttrValue(final MBeanInfo mbeaninfo, final Action1<ReadAttrResponse> action) {
        final Progressmeter progress = new Progressmeter();
        progress.setWidth("400px");
        status.appendChild(progress);
        progress.setValue(100);
        
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final EventQueueForwarder<Observer<ReadAttrResponse>> eqf = 
                new EventQueueForwarder(Observer.class, this._eventqueue);
        
        eqf.subscribe(new Observer<ReadAttrResponse>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(final ReadAttrResponse resp) {
                action.call(resp);
            }});
        
        final JolokiaRequest req = new JolokiaRequest();
        req.setType("read");
        req.setMBean(mbeaninfo.getObjectName().toString());
        
        this._signalClient.<ReadAttrResponse>defineInteraction(req, 
                Feature.ENABLE_LOGGING,
                Feature.ENABLE_COMPRESSOR,
                new SignalClient.UsingUri(this._jolokiauri),
                new SignalClient.UsingMethod(POST.class),
                new SignalClient.DecodeResponseAs(ReadAttrResponse.class)
                )
        .timeout(1, TimeUnit.SECONDS)
        .subscribe(eqf.subject());
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
    
    private void listMBeans(final Action1<ListResponse> action) {
        final Progressmeter progress = new Progressmeter();
        progress.setWidth("400px");
        status.appendChild(progress);
        progress.setValue(100);
        
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final EventQueueForwarder<Observer<ListResponse>> eqf = 
                new EventQueueForwarder(Observer.class, this._eventqueue);
        
        eqf.subscribe(new Observer<ListResponse>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(final ListResponse resp) {
                action.call(resp);
            }});
        
        final JolokiaRequest req = new JolokiaRequest();
        req.setType("list");
        
        this._signalClient.<ListResponse>defineInteraction(req, 
                Feature.ENABLE_LOGGING,
                Feature.ENABLE_COMPRESSOR,
                new SignalClient.UsingUri(_jolokiauri),
                new SignalClient.UsingMethod(POST.class),
                new SignalClient.DecodeResponseAs(ListResponse.class)
                )
        .timeout(1, TimeUnit.SECONDS)
        .subscribe(eqf.subject());
    }
    
    private ServiceData addServiceInfo(final ServiceInfo info) {
        return new ServiceData(
                info.getId(), 
                info.getHost(), 
                info.getUser(), 
                info.getService(),
                info.getBuildNo(),
                info.getJolokiaUrl(),
                new Action1<ServiceData>() {
                    @Override
                    public void call(final ServiceData data) {
                        try {
                            queryServiceMBeans(data);
                        } catch (URISyntaxException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }});
    }

    private void updateServicesModel(final ServiceData[] datas) {
        this.services.getChildren().clear();
        this.services.appendChild(new Columns() {
            private static final long serialVersionUID = 1L;
        {
            this.setSizable(true);
            GridBuilder.buildColumns(this, ServiceData.class);
        }});
        
        Arrays.sort(datas);
        this.services.setModel( GridBuilder.buildListModel(ServiceData.class, 
                datas.length, 
                GridBuilder.fetchPageOf(datas),
                GridBuilder.fetchTotalSizeOf(datas),
                GridBuilder.sortModelOf(datas)));
    }

    private ServiceData findServiceData(final String id) {
        final Iterator<ServiceData> iter = this._serviceDatas.iterator();
        while (iter.hasNext()) {
            final ServiceData data = iter.next();
            if (id.equals(data._id)) {
                return data;
            }
        }
        return null;
    }

    public static class HOST_ASC implements Comparator<ServiceData> {
        @Override
        public int compare(final ServiceData o1, final ServiceData o2) {
            return o1._host.compareTo(o2._host);
        }
    }

    public static class HOST_DSC implements Comparator<ServiceData> {
        @Override
        public int compare(final ServiceData o1, final ServiceData o2) {
            return o2._host.compareTo(o1._host);
        }
    }

    public static class USER_ASC implements Comparator<ServiceData> {
        @Override
        public int compare(final ServiceData o1, final ServiceData o2) {
            return o1._user.compareTo(o2._user);
        }
    }

    public static class USER_DSC implements Comparator<ServiceData> {
        @Override
        public int compare(final ServiceData o1, final ServiceData o2) {
            return o2._user.compareTo(o1._user);
        }
    }
    
    public static class SRV_ASC implements Comparator<ServiceData> {
        @Override
        public int compare(final ServiceData o1, final ServiceData o2) {
            return o1._service.compareTo(o2._service);
        }
    }

    public static class SRV_DSC implements Comparator<ServiceData> {
        @Override
        public int compare(final ServiceData o1, final ServiceData o2) {
            return o2._service.compareTo(o1._service);
        }
    }

    class ServiceData implements Comparable<ServiceData> {
        public ServiceData(final String id,
                final String host, 
                final String user, 
                final String service,
                final String buildNo,
                final String jolokiaUrl,
                final Action1<ServiceData> onShowJmx) {
            this._id = id;
            this._host = host;
            this._user = user;
            this._service = service;
            this._buildNo = buildNo;
            this._jolokiaUrl = jolokiaUrl;
            this._btnShowJmx = new Button("控制台");
            this._btnShowJmx.addEventListener(Events.ON_CLICK, 
                new EventListener<MouseEvent>() {
                    @Override
                    public void onEvent(MouseEvent event) throws Exception {
                        onShowJmx.call(ServiceData.this);
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
        
        public void addUsedMemoryInd(final Indicator ind) {
            final long value = ind.getValue();
            final int size = this._usedMemoryModel.getDataCount("usedMemory");
            if (size >= indHistorySize) {
                this._usedMemoryModel.addValue("usedMemory", ind.getTimestamp(), 
                        (int)( (double)value / 1024 / 1024), true);
            } else {
                this._usedMemoryModel.addValue("usedMemory", ind.getTimestamp(), 
                       (int)( (double)value / 1024 / 1024));
            }
        }

        private final String _id;

        @RowSource(name = "主机", asc = HOST_ASC.class, dsc = HOST_DSC.class)
        private final String _host;

        @RowSource(name = "用户", asc = USER_ASC.class, dsc = USER_DSC.class)
        private final String _user;

        @RowSource(name = "服务", asc = SRV_ASC.class, dsc = SRV_DSC.class)
        private final String _service;

        @RowSource(name = "构建号")
        private String _buildNo;

        @RowSource(name = "JMX")
        private final Button _btnShowJmx;

        @RowSource(name = "使用内存(MB)")
        private final ZHighCharts _chartMemory;

        private final SimpleExtXYModel _usedMemoryModel = new SimpleExtXYModel();

        private String _jolokiaUrl;

        @Override
        public int compareTo(final ServiceData o) {
            return this._id.compareTo(o._id);
        }
    }

    @Wire
    private Grid    services;
    
    private final List<ServiceData> _serviceDatas = new ArrayList<>();
    
    @Wire
    private Caption servicetitle;
    
    @Wire
    private Tree    mbeans;
    
    private SimpleTreeModel _model;

    @Wire
    private Center          attrs;
    
    @Wire
    private Center          ops;
    
    @Wire
    private Toolbarbutton   refresh;
    
    @Wire
    private Caption         status;
    
    @Wire
    private Intbox          indlen;
    
    private int             indHistorySize = 10;
    
    @Wire
    private Button          apply;
    
    @WireVariable("servicemonitor") 
    private ServiceMonitor _serviceMonitor;
    
    private Action0     _unsubscribeServiceStatus;
    
    private URI _jolokiauri;
    
    @WireVariable("signalClient") 
    private SignalClient _signalClient;
    
    private EventQueue<Event> _eventqueue;
}
