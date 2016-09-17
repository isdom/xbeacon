/**
 * 
 */
package org.jocean.zookeeper.webui.admin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.jocean.idiom.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueue;

import rx.functions.Action2;

/**
 * @author isdom
 *
 */
public class InvokeInEventQueue<T> implements InvocationHandler {
    private static final Logger LOG =
            LoggerFactory.getLogger(InvokeInEventQueue.class);

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
        final Action2<String, T> action = new Action2<String, T>() {
            @Override
            public void call(final String id, final T t) {
                if (id.equals(_id)) {
                    try {
                        method.invoke(t, args);
                    } catch (Exception e) {
                        LOG.warn("exception when invoke {}/{} for args {}, detail: {}",
                                t, method, args, ExceptionUtils.exception2detail(e));
                    }
                } else {
                    LOG.info("invoke {}, id {} NOT equals {}, just ignore.", 
                            method, id, _id);
                }
            }};
        this._eventqueue.publish(new Event(this._eventname, null, action));
        return null;
    }

    @SuppressWarnings("unchecked")
    public InvokeInEventQueue(final Class<T> intf, 
            final EventQueue<Event> eventqueue, 
            final String eventname, 
            final String id) {
        this._intfs = new Class[]{intf};
        this._eventqueue = eventqueue;
        this._eventname = eventname;
        this._id = id;
    }
    
    @SuppressWarnings("unchecked")
    public T buildInvoker() {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (T) Proxy.newProxyInstance(cl, this._intfs, this);
    }
    
    public EventListener<Event> asEventListener(final T t) {
        return new EventListener<Event>() {
            @SuppressWarnings("unchecked")
            @Override
            public void onEvent(final Event event) throws Exception {
                if ( event.getName().equals(_eventname)) {
                    ((Action2<String, T>)event.getData()).call(_id, t);
                }
            }};
    }

    private final Class<T>[] _intfs;
    private final EventQueue<Event> _eventqueue;
    private final String _eventname;
    private final String _id;
}
