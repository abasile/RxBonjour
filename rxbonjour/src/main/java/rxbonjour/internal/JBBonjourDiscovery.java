package rxbonjour.internal;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;

import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.Map;

import rx.Subscriber;
import rxbonjour.exc.DiscoveryFailed;

import rx.Observable;
import rxbonjour.exc.StaleContextException;
import rxbonjour.model.BonjourEvent;
import rxbonjour.model.BonjourService;

import static android.os.Build.VERSION_CODES.JELLY_BEAN;
import static android.os.Build.VERSION_CODES.LOLLIPOP;

/**
 * Bonjour implementation for Jelly-Bean and up, utilizing the NsdManager APIs.
 */
@TargetApi(JELLY_BEAN)
public final class JBBonjourDiscovery implements BonjourDiscovery {

	/** Discovery listener fed into the NsdManager */
//	private NsdManager.DiscoveryListener discoveryListener;

	/** NsdManager instance used for discovery, shared among subscribers */
	private NsdManager nsdManagerInstance;
	/** Synchronization lock on the NsdManager instance */
	private final Object nsdManagerLock = new Object();
	/** Number of subscribers listening to Bonjour events */
	private int subscriberCount = 0;

	/** Resolver backlog, necessary because of NsdManager's "one resolve at a time" limitation */
	private Backlog<NsdServiceInfo> resolveBacklog;

	/**
	 * Constructor
	 */
	public JBBonjourDiscovery() {
		super();
	}

	/* Begin private */


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

	@Override public Observable<BonjourEvent> start(Context context, final String type) {
		// Create a weak reference to the incoming Context
		final WeakReference<Context> weakContext = new WeakReference<>(context);

		Observable<BonjourEvent> obs = Observable.create(new Observable.OnSubscribe<BonjourEvent>() {
			@Override public void call(final Subscriber<? super BonjourEvent> subscriber) {
				Context context = weakContext.get();
				if (context == null) {
					subscriber.onError(new StaleContextException());
					return;
				}

				// Create the discovery listener
				final NsdManager.DiscoveryListener discoveryListener = new NsdManager.DiscoveryListener() {
					@Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
						subscriber.onError(new DiscoveryFailed(JBBonjourDiscovery.class, serviceType, errorCode));
					}

					@Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
						subscriber.onError(new DiscoveryFailed(JBBonjourDiscovery.class, serviceType, errorCode));
					}

					@Override public void onDiscoveryStarted(String serviceType) {

					}

					@Override public void onDiscoveryStopped(String serviceType) {

					}

					@Override public void onServiceFound(NsdServiceInfo serviceInfo) {
						// Add the found service to the resolve backlog (it will be processed once the backlog gets to it)
						resolveBacklog.add(serviceInfo);
					}

					@Override public void onServiceLost(NsdServiceInfo serviceInfo) {
						if (!subscriber.isUnsubscribed()) {
							subscriber.onNext(NSDHelper.newBonjourEvent(BonjourEvent.Type.REMOVED, serviceInfo));
						}
					}
				};

				// Obtain the NSD manager
				final NsdManager nsdManager = getNsdManager(context);

				// Create the resolver backlog
				if (resolveBacklog == null) {
					resolveBacklog = new Backlog<NsdServiceInfo>() {
						@Override public void onNext(Backlog<NsdServiceInfo> backlog, NsdServiceInfo info) {
							// Resolve this service info using the corresponding listener
							nsdManager.resolveService(info, new NsdManager.ResolveListener() {
								@Override public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
								}

								@Override public void onServiceResolved(NsdServiceInfo serviceInfo) {
									if (!subscriber.isUnsubscribed()) {
										subscriber.onNext(NSDHelper.newBonjourEvent(BonjourEvent.Type.ADDED, serviceInfo));
									}

									// Inform the backlog to continue processing
									if(resolveBacklog !=  null){
										resolveBacklog.proceed();
									}
								}
							});
						}
					};
				}

				// Add onUnsubscribe() hook
				subscriber.add(new MainThreadSubscription() {
					@Override protected void onUnsubscribe() {
						try {
							nsdManager.stopServiceDiscovery(discoveryListener);
							subscriberCount--;

						} catch (Exception ignored) {
							// "Service discovery not active on discoveryListener", thrown if starting the service discovery was unsuccessful earlier

						} finally {
							synchronized (nsdManagerLock) {
								if (subscriberCount <= 0) {
									resolveBacklog.quit();
									resolveBacklog = null;
									nsdManagerInstance = null;
								}
							}
						}
					}
				});

				// Start discovery
				nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
				subscriberCount++;
			}
		});

		// Share the observable to have multiple subscribers receive the same results emitted by the single DiscoveryListener
		return obs.share();
	}
}
