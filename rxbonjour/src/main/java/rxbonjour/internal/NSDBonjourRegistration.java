package rxbonjour.internal;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

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
public class NSDBonjourRegistration implements BonjourRegistration {


//	private NsdManager.DiscoveryListener discoveryListener;

    /** NsdManager instance used for discovery, shared among subscribers */
    private NsdManager nsdManagerInstance;
    /** Synchronization lock on the NsdManager instance */
    private final Object nsdManagerLock = new Object();

    Map<BonjourService,RegistrationListener> listeners = new HashMap<>();


    /**
     * Constructor
     */
    public NSDBonjourRegistration() {
        super();
    }



    /**
     * Returns the NsdManager shared among all subscribers for Bonjour events, creating it if necessary.
     *
     * @param context Context used to access the NsdManager
     * @return The NsdManager instance
     */
    private NsdManager getNsdManager(Context context) {
        synchronized (nsdManagerLock) {
            if (nsdManagerInstance == null) {
                nsdManagerInstance = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
            }
            return nsdManagerInstance;
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

                // Create the registration listener
                final RegistrationListener registrationListener = new RegistrationListener();
                registrationListener.setSubscriber(subscriber);

                // Obtain the NSD manager
                final NsdManager nsdManager = getNsdManager(context);
                // Start discovery
                nsdManager.registerService(NSDHelper.toNsdServiceInfo(service), NsdManager.PROTOCOL_DNS_SD, registrationListener);
            }
        });


        // Share the observable to have multiple subscribers receive the same results emitted by the single DiscoveryListener
        return obs.share();
    }

    @Override
    public Observable<BonjourEvent> unregister(Context context, final BonjourService service) {
        // Create a weak reference to the incoming Context
        final WeakReference<Context> weakContext = new WeakReference<>(context);

        final RegistrationListener registrationListener = listeners.get(service);
        Observable<BonjourEvent> obs = Observable.create(new Observable.OnSubscribe<BonjourEvent>() {
            @Override
            public void call(Subscriber<? super BonjourEvent> subscriber) {
                Context context = weakContext.get();
                if (context == null) {
                    subscriber.onError(new StaleContextException());
                    return;
                }

                registrationListener.setSubscriber(subscriber);

                // Obtain the NSD manager
                final NsdManager nsdManager = getNsdManager(context);
                // Start discovery
                nsdManager.unregisterService(registrationListener);
            }
        });



        // Share the observable to have multiple subscribers receive the same results emitted by the single DiscoveryListener
        return obs.share();
    }

    private class RegistrationListener implements NsdManager.RegistrationListener{
        Subscriber<? super BonjourEvent> subscriber;

        public void setSubscriber(Subscriber<? super BonjourEvent> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
            if (!subscriber.isUnsubscribed()) {
                BonjourService bonjourService = NSDHelper.toBonjourService(nsdServiceInfo);
                subscriber.onNext(new BonjourEvent(BonjourEvent.Type.REGISTERED, bonjourService));
                subscriber.onCompleted();
                listeners.put(bonjourService, this);
            }
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo nsdServiceInfo, int errorCode) {
            subscriber.onError(new RegistrationFailed(NSDBonjourRegistration.class, nsdServiceInfo.toString(), errorCode));
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo nsdServiceInfo) {
            if (!subscriber.isUnsubscribed()) {
                subscriber.onNext(NSDHelper.newBonjourEvent(BonjourEvent.Type.UNREGISTERED, nsdServiceInfo));
                subscriber.onCompleted();
                BonjourService bonjourService = NSDHelper.toBonjourService(nsdServiceInfo);
                listeners.remove(bonjourService);
            }
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        }
    }
}
