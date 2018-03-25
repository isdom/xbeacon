package org.jocean.xbeacon.jmxui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import org.jocean.http.ContentUtil;
import org.jocean.http.Feature;
import org.jocean.http.Interact;
import org.jocean.http.Interaction;
import org.jocean.http.MessageUtil;
import org.jocean.http.client.HttpClient;
import org.jocean.idiom.BeanFinder;
import org.jocean.idiom.Triple;
import org.jocean.idiom.rx.RxObservables;
import org.jocean.j2se.zk.ZKAgent;
import org.jocean.xbeacon.jmxui.bean.JolokiaRequest;
import org.jocean.xbeacon.jmxui.bean.LongValueResponse;
import org.jocean.zkoss.util.Desktops;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueue;
import org.zkoss.zk.ui.event.EventQueues;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.netty.handler.codec.http.HttpMethod;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class ServiceMonitor {
    private static final String UPDATE_EVENT = "update";
    
    public interface ServiceInfo {
        
        public String getId();
        
        public String getHost();

        public String getUser();

        public String getService();

        public String getBuildNo();
        
        public String getJolokiaUrl();
    }
    
    public interface Indicator {
        
        public long getTimestamp();
        
        public <V> V getValue();
    }
    
    public interface InitStatus extends Action1<Map<ServiceInfo, Map<String, Indicator[]>>> {
    }
    
    public interface UpdateStatus {
        
        public void onServiceAdded(final ServiceInfo info);
        
        public void onServiceUpdated(final ServiceInfo info);
        
        public void onServiceRemoved(final String id);
        
        public void onIndicator(final List<Triple<ServiceInfo, String, Indicator>> inds);
    }
    
    private static final Indicator[] EMPTY_IND = new Indicator[0];
    public static class ServiceInfoImpl implements ServiceInfo, Comparable<ServiceInfoImpl> {
        
        public ServiceInfoImpl(
                final String id,
                final String host, 
                final String user, 
                final String service) {
            this._id = id;
            this._host = host;
            this._user = user;
            this._service = service;
        }
        
        public String getId() {
            return this._id;
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
        
        private final String _id;
        private final String _host;
        private final String _user;
        private final String _service;
        
        private String _buildNo;
        
        private String _jolokiaUrl;
        
        private List<Indicator> _usedMemories = new ArrayList<>();

        @Override
        public int compareTo(final ServiceInfoImpl o) {
            return this._id.compareTo(o._id);
        }

        public ServiceInfo snapshot() {
            final String buildNo = this._buildNo;
            final String jolokiaUrl = this._jolokiaUrl;
            return new ServiceInfo() {

                @Override
                public String getId() {
                    return _id;
                }

                @Override
                public String getHost() {
                    return _host;
                }

                @Override
                public String getUser() {
                    return _user;
                }

                @Override
                public String getService() {
                    return _service;
                }

                @Override
                public String getBuildNo() {
                    return buildNo;
                }

                @Override
                public String getJolokiaUrl() {
                    return jolokiaUrl;
                }};
        }
    }
    
    @SuppressWarnings("unused")
    private static final Logger LOG = 
            LoggerFactory.getLogger(ServiceMonitor.class);

    public ServiceMonitor(final ZKAgent zkagent) {
        this._zkagent = zkagent;
        this._rootPath = zkagent.root();
        final ThreadGroup group = Thread.currentThread().getThreadGroup();
        this._executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                final Thread t = new Thread(group, r, "service-monitor-thread", 0);
                if (t.isDaemon())
                    t.setDaemon(false);
                if (t.getPriority() != Thread.NORM_PRIORITY)
                    t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }});
        this._scheduler = Schedulers.from(this._executor);
    }
    
    public void setRoot(final String rootPath) {
        this._rootPath = rootPath;
    }
    
    public Action0 subscribeServiceStatus(
            final int initIndSize,
            final InitStatus initStatus, 
            final UpdateStatus updateStatus) {
        final String initEvent = "init-" + UUID.randomUUID().toString();
        final EventListener<Event> listener = 
                buildEventListener(initStatus, updateStatus, initEvent, new AtomicBoolean(false));
        this._eventqueue.subscribe(listener);
        final Action0 unsubcribe = new Action0() {
            @Override
            public void call() {
                _eventqueue.unsubscribe(listener);
            }};
        
        Desktops.addActionForCurrentDesktopCleanup(unsubcribe);
        
        this._executor.execute(new Runnable() {
            @Override
            public void run() {
                publishServicesStatus(initIndSize, initEvent);
            }});
        return unsubcribe;
    }

    private EventListener<Event> buildEventListener(
            final InitStatus initStatus,
            final UpdateStatus updateStatus, 
            final String initEvent,
            final AtomicBoolean isInit) {
        return new EventListener<Event> () {
            @SuppressWarnings("unchecked")
            @Override
            public void onEvent(final Event event) throws Exception {
                if ( initEvent.equals(event.getName())) {
                    ((Action1<InitStatus>)event.getData()).call(initStatus);
                    isInit.set(true);
                } else if ( isInit.get() && UPDATE_EVENT.equals(event.getName())) {
                    ((Action1<UpdateStatus>)event.getData()).call(updateStatus);
                }
            }};
    }
    
    public void setWebapp(final WebApp webapp) {
        this._webapp = webapp;
    }

    public void start() throws Exception {
        this._eventqueue = EventQueues.lookup("service", this._webapp, true);
        startMonitorServices();
    }
    
    public void stop() {
        stopMonitorService();
        EventQueues.remove("service", this._webapp);
    }

    private void startMonitorServices() {
        this._monitorServicesSubscription = 
            RxObservables.fromAddListener(this._zkagent, "addListener", ZKAgent.Listener.class)
            .observeOn(this._scheduler, 1000)
            .subscribe(RxObservables.<ZKAgent.Listener>asOnNext(new ZKAgent.Listener() {
            @Override
            public void onAdded(final ZKAgent agent, 
                    final String absolutepath, final byte[] data) throws Exception {
                if (!isServiceStatusPath(absolutepath)) {
                    return;
                }
                final String path = absolute2relative(absolutepath);
                final ServiceInfoImpl impl = buildServiceInfo(path);
                if ( null != impl ) {
                    updateServiceInfo(impl, data);
                    _services.put(path, impl);
                    final ServiceInfo snapshot = impl.snapshot();
                    _eventqueue.publish(new Event(UPDATE_EVENT, null, new Action1<UpdateStatus>() {
                        @Override
                        public void call(final UpdateStatus updateStatus) {
                            updateStatus.onServiceAdded(snapshot);
                        }}));
                }
            }

            @Override
            public void onUpdated(final ZKAgent agent, 
                    String absolutepath, byte[] data) throws Exception {
                if (!isServiceStatusPath(absolutepath)) {
                    return;
                }
                final String path = absolute2relative(absolutepath);
                final ServiceInfoImpl impl = _services.get(path);
                if (null != impl) {
                    updateServiceInfo(impl, data);
                    final ServiceInfo snapshot = impl.snapshot();
                    _eventqueue.publish(new Event(UPDATE_EVENT, null, new Action1<UpdateStatus>() {
                        @Override
                        public void call(final UpdateStatus updateStatus) {
                            updateStatus.onServiceUpdated(snapshot);
                        }}));
                }
            }

            @Override
            public void onRemoved(final ZKAgent agent, 
                    String absolutepath) throws Exception {
                if (!isServiceStatusPath(absolutepath)) {
                    return;
                }
                final String path = absolute2relative(absolutepath);
                final ServiceInfo service = _services.remove(path);
                if (null != service) {
                    _eventqueue.publish(new Event(UPDATE_EVENT, null, new Action1<UpdateStatus>() {
                        @Override
                        public void call(final UpdateStatus updateStatus) {
                            updateStatus.onServiceRemoved(path);
                        }}));
                }
            }}));
        
        final JolokiaRequest req4usedMemory = new JolokiaRequest();
        req4usedMemory.setType("read");
        req4usedMemory.setMBean("java.lang:type=Memory");
        req4usedMemory.setAttribute("HeapMemoryUsage");
        req4usedMemory.setPath("used");
        
        final JolokiaRequest req4startTime = new JolokiaRequest();
        req4startTime.setType("read");
        req4startTime.setMBean("java.lang:type=Runtime");
        req4startTime.setAttribute("StartTime");
        
        final Action1<Triple<ServiceInfo, String, Indicator>> onUsedMemoryInd = ind -> {
              if (null != ind.third) {
                  final ServiceInfoImpl impl = (ServiceInfoImpl)ind.first;
                  impl._usedMemories.add(ind.third);
                  if (impl._usedMemories.size() > _maxIndSize) {
                      impl._usedMemories.remove(0);
                  }
              }
            };
            
        final Map<String, Action1<Triple<ServiceInfo, String, Indicator>>> name2action = Maps.newHashMap();
        name2action.put("usedMemory", onUsedMemoryInd);
        
        final String[] names = new String[]{"usedMemory", "startTime"};
        final JolokiaRequest[] reqs = new JolokiaRequest[]{req4usedMemory, req4startTime};
        this._future = this._executor.scheduleAtFixedRate(()->updateIndicators(names,  reqs, name2action), 
                5, 5, TimeUnit.SECONDS);
    }
    
    private void stopMonitorService() {
        this._future.cancel(false);
        this._monitorServicesSubscription.unsubscribe();
        this._executor.shutdown();
    }

    private ServiceInfoImpl buildServiceInfo(final String path) {
        final String[] pieces = path.split("\\.");
        if (pieces.length >= 4) {
            return new ServiceInfoImpl(path, pieces[1], pieces[2], pieces[3]);
        } else {
            return null;
        }
    }

    private void updateIndicators(
            final String[] indicatorNames, 
            final JolokiaRequest[] reqs, 
            final Map<String, Action1<Triple<ServiceInfo, String, Indicator>>> name2action) {
        final List<Observable<List<Triple<ServiceInfo, String, Indicator>>>> querys = Lists.newArrayList();
        for (ServiceInfoImpl impl : this._services.values()) {
            querys.add(queryLongIndicator(impl, indicatorNames, reqs));
        }
        Observable.merge(querys)
        .buffer(5, TimeUnit.SECONDS)
        .observeOn(this._scheduler)
        .subscribe(indslist -> {
                final List<Triple<ServiceInfo, String, Indicator>> notify = Lists.newArrayList();
                for (List<Triple<ServiceInfo, String, Indicator>> inds : indslist) {
                    notify.addAll(inds);
                    for (Triple<ServiceInfo, String, Indicator> ind : inds) {
                        final Action1<Triple<ServiceInfo, String, Indicator>> action = 
                                name2action.get(ind.second);
                        if (null != action) {
                            action.call(ind);
                        }
                    }
                }
                _eventqueue.publish(new Event(UPDATE_EVENT, null, new Action1<UpdateStatus>() {
                    @Override
                    public void call(final UpdateStatus updateStatus) {
                        updateStatus.onIndicator(notify);
                    }}));
            });
    }
    
    private Observable<List<Triple<ServiceInfo, String, Indicator>>> queryLongIndicator(
            final ServiceInfoImpl impl, final String[] names, final JolokiaRequest[] reqs) {
        return this._finder.find(HttpClient.class).map(client->MessageUtil.interact(client))
            .flatMap(sendreq(impl.getJolokiaUrl(), reqs))
            .compose(MessageUtil.responseAs(LongValueResponse[].class, MessageUtil::unserializeAsJson))
            .timeout(1, TimeUnit.SECONDS)
            .onErrorResumeNext(resp404(reqs.length))
            .map(resp2indicator(impl, names));
    }

    private Func1<Interact, Observable<? extends Interaction>> sendreq(final String suri, final Object req) {
        return interact-> { 
            try {
                return interact.method(HttpMethod.POST).uri(suri).path(new URI(suri).getRawPath())
                        .body(req, ContentUtil.TOJSON)
                        .feature(Feature.ENABLE_LOGGING, Feature.ENABLE_COMPRESSOR)
                        .execution();
            } catch (Exception e) {
                return Observable.error(e);
            }
        };
    }

    private Func1<? super LongValueResponse[], ? extends List<Triple<ServiceInfo, String, Indicator>>> resp2indicator(
            final ServiceInfoImpl impl, final String[] names) {
        return resps -> {
            final List<Triple<ServiceInfo, String, Indicator>> inds = Lists.newArrayList();
            for (int idx=0; idx < resps.length; idx++) {
                final LongValueResponse resp = resps[idx];
                if (200 == resp.getStatus()) {
                    inds.add(Triple.of((ServiceInfo)impl, names[idx], indicator(resp.getTimestamp(), resp.getValue())));
                } else {
                    inds.add(Triple.of((ServiceInfo)impl, names[idx], (Indicator)null));
                }
            }
            return inds;
        };
    }

    private Indicator indicator(final long timestamp, final Long value) {
        return new Indicator() {
            @Override
            public long getTimestamp() {
                return timestamp;
            }
            @SuppressWarnings("unchecked")
            @Override
            public <V> V getValue() {
                return (V)value;
            }};
    }

    private Observable<LongValueResponse[]> resp404(final int count) {
        final List<LongValueResponse> resps = Lists.newArrayList();
        for (int idx = 0; idx < count; idx++) {
            final LongValueResponse resp = new LongValueResponse();
            resp.setStatus(404);
            resps.add(resp);
        }
        return Observable.just(resps.toArray(new LongValueResponse[0]));
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

    private void updateServiceInfo(final ServiceInfoImpl service, final byte[] data)
            throws IOException {
        final Properties prop = new Properties();
        prop.load(new ByteArrayInputStream(data));
        service.setBuildNo(prop.getProperty("build.no"));
        service.setJolokiaUrl(prop.getProperty("jolokia.url"));
    }

    private void publishServicesStatus(final int initIndSize, final String initEvent) {
        // run in services info update executor
        final Map<ServiceInfo, Map<String,Indicator[]>> status = Maps.newHashMap();
        for (ServiceInfoImpl impl : this._services.values()) {
            final Map<String, Indicator[]> indicators = Maps.newHashMap();
            final int size = impl._usedMemories.size();
            
            indicators.put("usedMemory", 
                impl._usedMemories.subList(Math.max(0, size - initIndSize), size)
                    .toArray(EMPTY_IND));
            status.put(impl.snapshot(), indicators);
        }
        this._eventqueue.publish(new Event(initEvent, null, new Action1<InitStatus>() {
            @Override
            public void call(final InitStatus initStatus) {
                initStatus.call(status);
            }}));
    }

    private WebApp _webapp;
    private String _rootPath;
    private EventQueue<Event> _eventqueue;
    
    private final ZKAgent _zkagent;
    private final ScheduledExecutorService _executor;
    private final Scheduler _scheduler;
    private final Map<String, ServiceInfoImpl> _services = Maps.newHashMap();
    
    private Subscription _monitorServicesSubscription;
    private ScheduledFuture<?> _future;
    
    @Inject
    private BeanFinder _finder;
    
    private int _maxIndSize = 1000;
}
