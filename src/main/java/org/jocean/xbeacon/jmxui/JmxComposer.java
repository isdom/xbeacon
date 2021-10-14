package org.jocean.xbeacon.jmxui;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jocean.http.RpcRunner;
import org.jocean.idiom.BeanFinder;
import org.jocean.idiom.Triple;
import org.jocean.jolokia.JolokiaAPI;
import org.jocean.jolokia.api.ExecResponse;
import org.jocean.jolokia.api.JolokiaRequest;
import org.jocean.jolokia.api.ListResponse;
import org.jocean.jolokia.api.ListResponse.DomainInfo;
import org.jocean.jolokia.api.ListResponse.MBeanInfo;
import org.jocean.jolokia.api.ListResponse.OperationInfo;
import org.jocean.jolokia.api.ReadAttrResponse;
import org.jocean.svr.FinderUtil;
import org.jocean.xbeacon.jmxui.ServiceMonitor.Indicator;
import org.jocean.xbeacon.jmxui.ServiceMonitor.InitStatus;
import org.jocean.xbeacon.jmxui.ServiceMonitor.ServiceInfo;
import org.jocean.xbeacon.jmxui.ServiceMonitor.UpdateStatus;
import org.jocean.zkoss.annotation.RowSource;
import org.jocean.zkoss.builder.UIBuilders;
import org.jocean.zkoss.builder.ZModels;
import org.jocean.zkoss.model.SimpleTreeModel;
import org.jocean.zkoss.ui.JSON2TreeModel;
import org.jocean.zkoss.util.EventQueueForwarder;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
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
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listhead;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Progressmeter;
import org.zkoss.zul.Toolbarbutton;
import org.zkoss.zul.Tree;
import org.zkoss.zul.Treecell;
import org.zkoss.zul.Treecol;
import org.zkoss.zul.Treecols;
import org.zkoss.zul.Treeitem;
import org.zkoss.zul.TreeitemRenderer;
import org.zkoss.zul.Treerow;
import org.zkoss.zul.Window;

import com.alibaba.fastjson.JSONArray;

import rx.Observable;
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

    private static final PeriodFormatter PERIODFMT = new PeriodFormatterBuilder()
                .appendYears()
                .appendSuffix(" 年 ")
//                .printZeroRarelyLast()
                .appendMonths()
                .appendSuffix(" 月 ")
//                .printZeroRarelyLast()
                .appendWeeks()
                .appendSuffix(" 星期 ")
                .appendDays()
                .appendSuffix(" 天 ")
//                .printZeroRarelyLast()
                .appendHours()
                .appendSuffix(" 小时 ")
//                .printZeroRarelyLast()
                .appendMinutes()
                .appendSuffix(" 分钟 ")
//                .printZeroRarelyLast()
                .appendSeconds()
                .appendSuffix(" 秒")
                .toFormatter();

    class NodeTreeRenderer implements TreeitemRenderer<SimpleTreeModel.Node> {
        public NodeTreeRenderer() {
            this._initOpened = false;
        }

        public NodeTreeRenderer(final boolean open) {
            this._initOpened = open;
        }

        private final boolean _initOpened;

        @Override
        public void render(final Treeitem item, final SimpleTreeModel.Node node, final int index)
                throws Exception {
            item.setValue(node);
            item.appendChild( new Treerow() {
                private static final long serialVersionUID = 1L;
            {
                this.appendChild(new Treecell(node.getName()));
            }});
            if (this._initOpened) {
                item.setOpen(true);
            }
        }
    }

	@Override
    public void doAfterCompose(final Window comp) throws Exception {
		super.doAfterCompose(comp);

//        this.services.setRowRenderer(
//                UIBuilders.buildRowRenderer(ServiceData.class));
        this.services.setItemRenderer(
                UIBuilders.buildItemRenderer(ServiceData.class));
        this.mbeans.setItemRenderer(new NodeTreeRenderer());

        this.mbeans.addEventListener(Events.ON_SELECT, showSelectedMBean());
        this.refresh.addEventListener(Events.ON_CLICK, showSelectedMBean());

        this._eventqueue = EventQueues.lookup("callback", EventQueues.SESSION, true);

        this.apply.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(final Event event) throws Exception {
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
                    for (final Map.Entry<ServiceInfo, Map<String, Indicator[]>> entry : status.entrySet()) {
                        final ServiceInfo info = entry.getKey();
                        final ServiceData data = addServiceInfo(info);
                        final Indicator[] inds = entry.getValue().get("usedMemory");
                        if (null != inds) {
                            for (final Indicator ind : inds) {
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
                    for (final Triple<ServiceInfo, String, Indicator> ind : inds) {
                        if (null != ind.third) {
                            final ServiceData data = findServiceData(ind.first.getId());
                            if (null != data) {
                                if ("usedMemory".equals(ind.second)) {
                                    if (null != ind.third) {
                                        data.addUsedMemoryInd(ind.third);
                                    }
                                } else if ("startTime".equals(ind.second)) {
                                    if (null != ind.third) {
                                        final long startTime = (Long)ind.third.getValue();
                                        final long durationInSecond = ind.third.getTimestamp() - startTime/1000;
                                        final Period period = new Period(durationInSecond * 1000L);
                                        final String periodAsString = PERIODFMT.print(period.normalizedStandard());

                                        data.setServiceTime(periodAsString);
                                        if (LOG.isDebugEnabled()) {
                                            LOG.debug("update service time for {}: total {} seconds / {}",
                                                    data._service, durationInSecond, periodAsString);
                                        }
                                    }
                                }
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
        listMBeans(resp-> {
                if ( 200 == resp.getStatus()) {
                    showServiceMBeans(resp);
                }
            });
    }

    private void showServiceMBeans(final ListResponse resp) {
        final DomainInfo[] domains = resp.getDomains();
        final SimpleTreeModel model = new SimpleTreeModel(new SimpleTreeModel.Node(""));
        for (final DomainInfo domain : domains) {
            final SimpleTreeModel.Node child =
                model.getRoot().addChildIfAbsent(domain.getName());
            for (final MBeanInfo mbeaninfo : domain.getMBeans()) {
                final SimpleTreeModel.Node mbeannode =
                        child.addChildrenIfAbsent(buildPath(
                                mbeaninfo.getObjectName().getKeyPropertyListString()));
                mbeannode.setData(mbeaninfo);
            }
        }

        this.mbeans.setModel(model);
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
	    showMBeanOperations(mbeaninfo, op -> lauchOperation(mbeaninfo, op));
	    queryAttrValue(mbeaninfo,
            resp -> showMBeanAttributes(resp),
            e -> {
                status.getChildren().clear();
                Messagebox.show("query " + mbeaninfo.getObjectName() + " attribute failed, detail: " + e.getMessage(),
                        "query attribute result", Messagebox.OK, Messagebox.ERROR);
            });
    }

    private void buildArgsGrid(final OperationUI op, final Grid grid) {
        grid.setRowRenderer(UIBuilders.buildRowRenderer(ArgUI.class));
        grid.setSizedByContent(true);
        grid.appendChild(new Columns() {
            private static final long serialVersionUID = 1L;
        {
            this.setSizable(true);
            UIBuilders.buildColumns(this, ArgUI.class);
        }});
        grid.setModel( ZModels.buildListModel(
                op.getArgs().length,
                ZModels.fetchPageOf(op.getArgs()),
                ZModels.fetchTotalSizeOf(op.getArgs())));
    }

    private void lauchOperation(final MBeanInfo mbeaninfo, final OperationUI op) {
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
                this.addEventListener(Events.ON_CLICK, event->{
                        final JSONArray args = op.genArgArray();
                        Messagebox.show("invoke " + op.getName() + " with args(" + args + ")?",
                                "exec operation", Messagebox.OK | Messagebox.CANCEL, Messagebox.QUESTION,
                                ev2->{
                                        if (Messagebox.ON_OK.equals(ev2.getName())){
                                            invokeOperation(mbeaninfo, op, args);
                                        }
                                    });

                    });
            }
            };
        dialog.appendChild(btnExec);
        dialog.doModal();
    }

    private void invokeOperation(final MBeanInfo mbean, final OperationUI op, final JSONArray args) {
        final Observable<RpcRunner> rpcs =
                // remark temp 10.14
//                Observable.empty();
                 FinderUtil.rpc(this._finder).runner();

        final JolokiaRequest req = new JolokiaRequest();
        req.setType("exec");
        req.setMBean(mbean.getObjectName().toString());
        req.setOperation(op.genNameWithSignature());
        req.setArguments(args);

        final ExecResponse resp = this._finder.find(JolokiaAPI.class).flatMap(
            api->rpcs.compose(api.exec(_jolokiauri.toString(), req)))
            .toBlocking().single();
        if (200 == resp.getStatus()) {
            Messagebox.show("invoke " + op.getName() + " success, return: " + resp.getValue(),
                    "exec operation result", Messagebox.OK, Messagebox.INFORMATION);
        } else {
            Messagebox.show("invoke " + op.getName() + " failed, status code is " + resp.getStatus(),
                    "exec operation result", Messagebox.OK, Messagebox.ERROR);
        }
    }

    private void showMBeanOperations(final MBeanInfo mbeaninfo, final Action1<OperationUI> invokeOperation) {
        this.ops.getChildren().clear();

        final OperationInfo[] infos = mbeaninfo.getOperations();

        if (null != infos) {
            final OperationUI[] uis = new OperationUI[infos.length];
            for (int idx=0; idx < infos.length; idx++) {
                uis[idx] = new OperationUI(infos[idx]);
                uis[idx].setInvoker(invokeOperation);
            }

            final Grid grid = new Grid();
            this.ops.appendChild(grid);
            grid.setRowRenderer(UIBuilders.buildRowRenderer(OperationUI.class));
            grid.setSizedByContent(true);
            grid.appendChild(new Columns() {
                private static final long serialVersionUID = 1L;
            {
                this.setSizable(true);
                UIBuilders.buildColumns(this, OperationUI.class);
            }});
            grid.setModel( ZModels.buildListModel(
                    uis.length,
                    ZModels.fetchPageOf(uis),
                    ZModels.fetchTotalSizeOf(uis)));
        }
    }

    class AttributeRenderer implements TreeitemRenderer<SimpleTreeModel.Node> {
        public AttributeRenderer() {
            this._initOpened = false;
        }

        public AttributeRenderer(final boolean open) {
            this._initOpened = open;
        }

        private final boolean _initOpened;

        @Override
        public void render(final Treeitem item, final SimpleTreeModel.Node node, final int index)
                throws Exception {
            item.setValue(node);
            item.appendChild(new Treerow() {
                private static final long serialVersionUID = 1L;
            {
                this.appendChild(new Treecell(node.getName()));
                if (null != node.getData()) {
                    final Object value = node.getData();
                    this.appendChild(new Treecell(value.toString()));
                    this.appendChild(new Treecell(value.getClass().getSimpleName()));
                }
            }});
            if (this._initOpened) {
                item.setOpen(true);
            }
        }
    }

    private void showMBeanAttributes(final ReadAttrResponse resp) {
        final List<Component> children = this.attrs.getChildren();
        while (!children.isEmpty()) {
            children.remove(0);
        }

        this.attrs.appendChild(
                new Tree() {
                    private static final long serialVersionUID = 1L;

                {
                    this.appendChild(new Treecols() {
                        private static final long serialVersionUID = 1L;
                    {
                        this.setSizable(true);
                        this.appendChild(new Treecol("名称") {
                            private static final long serialVersionUID = 1L;
                        {
                            this.setWidth("200px");
                        }});
                        this.appendChild(new Treecol("内容") {
                            private static final long serialVersionUID = 1L;
                        {
                        }});
                        this.appendChild(new Treecol("类型") {
                            private static final long serialVersionUID = 1L;
                        {
                            this.setWidth("50px");
                        }});
                    }});
                    this.setItemRenderer(new AttributeRenderer(true));
                    this.setModel(JSON2TreeModel.buildTree(resp.getValue()));
                }}
//                new Hlayout() {
//                    private static final long serialVersionUID = 1L;
//                {
//                    this.appendChild(JsonUI.buildUI(resp.getValue()));
//                }}
                );
        this.status.getChildren().clear();
    }

    private void queryAttrValue(final MBeanInfo mbeaninfo,
            final Action1<ReadAttrResponse> action,
            final Action1<Throwable> onError) {
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
            public void onError(final Throwable e) {
                onError.call(e);
            }

            @Override
            public void onNext(final ReadAttrResponse resp) {
                if (200 == resp.getStatus()) {
                    action.call(resp);
                } else {
                    onError.call(new RuntimeException("status:" + resp.getStatus() + "\n"
                        + "error:" + resp.getError() + "\n"
                        + "error_type:" + resp.getErrorType() + "\n"
                        + "stacktrace:"+ resp.getStacktrace()));
                }
            }});

        final Observable<RpcRunner> rpcs =
                // remark temp 10.14
//                Observable.empty();
                 FinderUtil.rpc(this._finder).runner();

        this._finder.find(JolokiaAPI.class).flatMap(api->rpcs.compose(
                    api.readAttribute(_jolokiauri.toString(), mbeaninfo.getObjectName().toString())))
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
            public void onError(final Throwable e) {
            }

            @Override
            public void onNext(final ListResponse resp) {
                action.call(resp);
            }});

        final Observable<RpcRunner> rpcs =
                // remark temp 10.14
//                Observable.empty();
                 FinderUtil.rpc(this._finder).runner();

        this._finder.find(JolokiaAPI.class).flatMap(
            api->rpcs.compose(api.list(_jolokiauri.toString())) )
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
                        } catch (final URISyntaxException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }});
    }

    private void updateServicesModel(final ServiceData[] datas) {
        final List<Component> children = this.services.getChildren();
        while (!children.isEmpty()) {
            children.remove(0);
        }

//        this.services.appendChild(new Columns() {
//            private static final long serialVersionUID = 1L;
//        {
//            this.setSizable(true);
//            UIBuilders.buildColumns(this, ServiceData.class);
//        }});
        this.services.appendChild(new Listhead() {
            private static final long serialVersionUID = 1L;
            {
                this.setSizable(true);
                UIBuilders.buildHead(this, ServiceData.class);
            }
        });

        Arrays.sort(datas);
        this.services.setModel( ZModels.buildListModel(
                datas.length,
                ZModels.fetchPageOf(datas),
                ZModels.fetchTotalSizeOf(datas),
                ZModels.sortModelOf(datas)));
//        this.services.setSizedByContent(true);
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
                    public void onEvent(final MouseEvent event) throws Exception {
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

        public void setServiceTime(final String serviceTime) {
            this._serviceTime.setValue(serviceTime);
        }

        private final String _id;

        @RowSource(name = "主机", asc = HOST_ASC.class, dsc = HOST_DSC.class)
        private final String _host;

        @RowSource(name = "用户", asc = USER_ASC.class, dsc = USER_DSC.class)
        private final String _user;

        @RowSource(name = "服务", asc = SRV_ASC.class, dsc = SRV_DSC.class)
        private final String _service;

        @RowSource(name = "运行时长")
        private final Label _serviceTime = new Label("<未知>");

        @RowSource(name = "构建号")
        private final String _buildNo;

        @RowSource(name = "JMX")
        private final Button _btnShowJmx;

        @RowSource(name = "使用内存(MB)")
        private final ZHighCharts _chartMemory;

        private final SimpleExtXYModel _usedMemoryModel = new SimpleExtXYModel();

        private final String _jolokiaUrl;

        @Override
        public int compareTo(final ServiceData o) {
            return this._id.compareTo(o._id);
        }
    }

    @Wire
    private Listbox services;

//    private Grid    services;

    private final List<ServiceData> _serviceDatas = new ArrayList<>();

    @Wire
    private Caption servicetitle;

    @Wire
    private Tree    mbeans;

    @Wire
    private Center          attrs;
//    private Hlayout attrs;

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

    @WireVariable("beanFinder")
    private BeanFinder _finder;

    private EventQueue<Event> _eventqueue;
}
