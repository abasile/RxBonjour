package rxbonjour.internal;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.impl.DNSIncoming;
import javax.jmdns.impl.ServiceInfoImpl;
import javax.jmdns.impl.constants.DNSRecordClass;
import javax.jmdns.impl.constants.DNSRecordType;

import rx.Observable;
import rx.Subscriber;
import rxbonjour.exc.DiscoveryFailed;
import rxbonjour.exc.RegistrationFailed;
import rxbonjour.exc.StaleContextException;
import rxbonjour.model.BonjourEvent;
import rxbonjour.model.BonjourService;

import static android.os.Build.VERSION_CODES.JELLY_BEAN;

/**
 * Created on 12/11/2015.
 */
@TargetApi(JELLY_BEAN)
public class SupportBonjourRegistration implements BonjourRegistration {

    static {
        // Disable logging for some JmDNS classes, since those severely clutter log output
        Logger.getLogger(DNSIncoming.class.getName()).setLevel(Level.OFF);
        Logger.getLogger(DNSRecordType.class.getName()).setLevel(Level.OFF);
        Logger.getLogger(DNSRecordClass.class.getName()).setLevel(Level.OFF);
        Logger.getLogger(DNSIncoming.MessageInputStream.class.getName()).setLevel(Level.OFF);
    }

    /** Suffix appended to input types */
    private static final String SUFFIX = ".local.";

    /** Tag to associate with the multicast lock */
    private static final String LOCK_TAG = "RxBonjourDiscovery";

    /** The JmDNS instance used for discovery, shared among subscribers */
    private JmDNS jmdnsInstance;
    /** Synchronization lock on the JmDNS instance */
    private final Object jmdnsLock = new Object();
    /** Number of subscribers listening to Bonjour events */
    private int subscriberCount = 0;


    /**
     * Constructor
     */
    public SupportBonjourRegistration() {
        super();
    }



	/* Begin private */

    /**
     * Returns the current connection's IP address.
     * This implementation is taken from http://stackoverflow.com/a/13677686/1143172
     * and takes note of a JmDNS issue with resolved IP addresses.
     *
     * @param wifiManager WifiManager to look up the IP address from
     * @return The InetAddress of the current connection
     * @throws IOException In case the InetAddress can't be resolved
     */
    private InetAddress getInetAddress(WifiManager wifiManager) throws IOException {
        int intaddr = wifiManager.getConnectionInfo().getIpAddress();

        byte[] byteaddr = new byte[] { (byte) (intaddr & 0xff), (byte) (intaddr >> 8 & 0xff),
                (byte) (intaddr >> 16 & 0xff), (byte) (intaddr >> 24 & 0xff) };
        return InetAddress.getByAddress(byteaddr);
    }

    /**
     * Returns the JmDNS shared among all subscribers for Bonjour events, creating it if necessary.
     *
     * @param wifiManager WifiManager used to access the device's IP address with which JmDNS is initialized
     * @return The JmDNS instance
     * @throws IOException In case the device's address can't be resolved
     */
    private JmDNS getJmdns(WifiManager wifiManager) throws IOException {
        synchronized (jmdnsLock) {
            if (jmdnsInstance == null) {
                InetAddress inetAddress = getInetAddress(wifiManager);
                jmdnsInstance = JmDNS.create(inetAddress, inetAddress.toString());
            }
            return jmdnsInstance;
        }
    }


/* Begin overrides */

    @Override public Observable<BonjourEvent> register(Context context, final BonjourService service)  {
        // Create a weak reference to the incoming Context
        final WeakReference<Context> weakContext = new WeakReference<>(context);


        Observable<BonjourEvent> obs = Observable.create(new Observable.OnSubscribe<BonjourEvent>() {
            @Override public void call(Subscriber<? super BonjourEvent> subscriber) {
                Context context = weakContext.get();
                if (context == null) {
                    subscriber.onError(new StaleContextException());
                    return;
                }

                // Obtain a multicast lock from the Wifi Manager and acquire it
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                final WifiManager.MulticastLock lock = wifiManager.createMulticastLock(LOCK_TAG);
                lock.setReferenceCounted(true);
                lock.acquire();

                // Obtain the current IP address and initialize JmDNS' discovery service with that
                try {
                    final JmDNS jmdns = getJmdns(wifiManager);

                    // Start discovery
                    jmdns.registerService(bonjourToServiceInfo(service));
                    subscriber.onNext(new BonjourEvent(BonjourEvent.Type.REGISTERED, service));
                    subscriber.onCompleted();

                } catch (IOException e) {
                    subscriber.onError(new RegistrationFailed(SupportBonjourRegistration.class, service.getName()));
                }
                finally {
                    lock.release();
                }

            }
        });


        // Share the observable to have multiple subscribers receive the same results emitted by the single DiscoveryListener
        return obs.share();
    }

    ServiceInfo bonjourToServiceInfo(BonjourService bonjourService) {
        return new ServiceInfoImpl(bonjourService.getType(), bonjourService.getName(), null, bonjourService.getPort(), 1, 1, true, "");
    }

    @Override
    public Observable<BonjourEvent> unregister(Context context, final BonjourService service) {
        // Create a weak reference to the incoming Context
        final WeakReference<Context> weakContext = new WeakReference<>(context);


        Observable<BonjourEvent> obs = Observable.create(new Observable.OnSubscribe<BonjourEvent>() {
            @Override public void call(Subscriber<? super BonjourEvent> subscriber) {
                Context context = weakContext.get();
                if (context == null) {
                    subscriber.onError(new StaleContextException());
                    return;
                }

                // Obtain a multicast lock from the Wifi Manager and acquire it
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                final WifiManager.MulticastLock lock = wifiManager.createMulticastLock(LOCK_TAG);
                lock.setReferenceCounted(true);
                lock.acquire();

                // Obtain the current IP address and initialize JmDNS' discovery service with that
                try {
                    final JmDNS jmdns = getJmdns(wifiManager);

                    // Start discovery
                    jmdns.unregisterAllServices();
                    subscriber.onNext(new BonjourEvent(BonjourEvent.Type.UNREGISTERED, service));
                    subscriber.onCompleted();

                } catch (IOException e) {
                    subscriber.onError(new RegistrationFailed(SupportBonjourRegistration.class, service.getName()));
                }

            }
        });


        // Share the observable to have multiple subscribers receive the same results emitted by the single DiscoveryListener
        return obs.share();
    }

}
