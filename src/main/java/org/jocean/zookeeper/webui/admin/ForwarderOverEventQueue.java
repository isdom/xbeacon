/**
 * 
 */
package org.jocean.zookeeper.webui.admin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

import org.jocean.idiom.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueue;

import rx.functions.Action1;

/**
 * @author isdom
 *
 */
public class ForwarderOverEventQueue<T> implements InvocationHandler {
    private static final Logger LOG =
            LoggerFactory.getLogger(ForwarderOverEventQueue.class);

    public Object invoke(final Object obj, final Method method, final Object[] args)
            throws Throwable {
        // An invocation of the hashCode, equals, or toString methods
        // declared in java.lang.Object on a proxy instance will be 
        // encoded and dispatched to the invocation handler's invoke
        // method in the same manner as interface method invocations are
        // encoded and dispatched, as described above. The declaring 
        // class of the Method object passed to invoke will be
        // java.lang.Object. Other public methods of a proxy instance
        // inherited from java.lang.Object are not overridden by a proxy
        // class, so invocations of those methods behave like they do
        // for instances of java.lang.Object.
        if (method.getName().equals("hashCode")) {
            return this.hashCode();
        } else if (method.getName().equals("equals")) {
            return (this == args[0]);
        } else if (method.getName().equals("toString")) {
            return this.toString();
        }
        final Action1<T> action = new Action1<T>() {
            @Override
            public void call(final T t) {
                try {
                    method.invoke(t, args);
                } catch (Exception e) {
                    LOG.warn("exception when invoke {}/{} for args {}, detail: {}",
                            t, method, args, ExceptionUtils.exception2detail(e));
                }
            }};
        this._eventqueue.publish(new Event(this._eventid, null, action));
        return null;
    }

    @SuppressWarnings("unchecked")
    public ForwarderOverEventQueue(final Class<T> intf, 
            final EventQueue<Event> eventqueue) {
        this._intfs = new Class[]{intf};
        this._eventqueue = eventqueue;
        this._eventid = UUID.randomUUID().toString();
    }
    
    @SuppressWarnings("unchecked")
    public T subject() {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (T) Proxy.newProxyInstance(cl, this._intfs, this);
    }
    
    public void subscribe(final T observer) {
        this._eventqueue.subscribe(asEventListener(observer));
    }
    
    private EventListener<Event> asEventListener(final T observer) {
        return new EventListener<Event>() {
            @SuppressWarnings("unchecked")
            @Override
            public void onEvent(final Event event) throws Exception {
                if ( event.getName().equals(_eventid)) {
                    ((Action1<T>)event.getData()).call(observer);
                }
            }};
    }

    private final Class<T>[] _intfs;
    private final EventQueue<Event> _eventqueue;
    private final String _eventid;
}
