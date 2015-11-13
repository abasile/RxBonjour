package rxbonjour.exc;

import android.net.nsd.NsdServiceInfo;

import rxbonjour.internal.BonjourDiscovery;
import rxbonjour.internal.BonjourRegistration;

/**
 * Created on 12/11/2015.
 */
public class RegistrationFailed extends Exception{

    public RegistrationFailed(Class<? extends BonjourRegistration> implClass, String service, int errorCode) {
        super(implClass.getSimpleName() + " registration failed for service " + service + " with error code " + errorCode);
    }

    public RegistrationFailed(Class<? extends BonjourRegistration> implClass, String service) {
        super(implClass.getSimpleName() + " registration failed for service " + service);
    }
}
