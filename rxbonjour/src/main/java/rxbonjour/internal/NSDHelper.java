package rxbonjour.internal;

import android.annotation.TargetApi;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;

import java.nio.charset.Charset;
import java.util.Map;

import rxbonjour.model.BonjourEvent;
import rxbonjour.model.BonjourService;

import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;

/**
 * Created on 12/11/2015.
 */
public class NSDHelper {

    /**
     * Creates a new BonjourEvent instance from an Nsd Service info object.
     *
     * @param type        Type of event, either ADDED or REMOVED
     * @param serviceInfo ServiceInfo containing information about the changed service
     * @return A BonjourEvent containing the necessary information
     */
    @TargetApi(LOLLIPOP) public static BonjourEvent newBonjourEvent(BonjourEvent.Type type, NsdServiceInfo serviceInfo) {
        // Create and return an event wrapping the BonjourService
        return new BonjourEvent(type, toBonjourService(serviceInfo));
    }

    @TargetApi(KITKAT) public static BonjourService toBonjourService( NsdServiceInfo serviceInfo){
        // Construct a new BonjourService
        BonjourService.Builder serviceBuilder = new BonjourService.Builder(serviceInfo.getServiceName(), serviceInfo.getServiceType());
        // Prepare TXT record Bundle (on Lollipop and up)
        if (Build.VERSION.SDK_INT >= LOLLIPOP) {
            Map<String, byte[]> attributes = serviceInfo.getAttributes();
            for (String key : attributes.keySet()) {
                serviceBuilder.addTxtRecord(key, new String(attributes.get(key), Charset.forName("UTF-8")));
            }
        }

        // Add host address and port
        serviceBuilder.addAddress(serviceInfo.getHost());
        serviceBuilder.setPort(serviceInfo.getPort());
        return serviceBuilder.build();
    }

    @TargetApi(KITKAT) public static NsdServiceInfo toNsdServiceInfo(BonjourService bonjourService){
        NsdServiceInfo nsdServiceInfo = new NsdServiceInfo();
        nsdServiceInfo.setServiceName(bonjourService.getName());
        nsdServiceInfo.setServiceType(bonjourService.getType());
        nsdServiceInfo.setPort(bonjourService.getPort());
        return nsdServiceInfo;
    }
}
