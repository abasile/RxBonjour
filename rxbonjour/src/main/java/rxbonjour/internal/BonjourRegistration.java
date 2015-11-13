package rxbonjour.internal;

import android.content.Context;

import rxbonjour.model.BonjourEvent;
import rxbonjour.model.BonjourService;

/**
 * Base interface for Services Register
 */

public interface BonjourRegistration {

    /**
     * Register a Bonjour service
     *
     * @param context Context of the request
     * @param service Service to resgister
     * @return An Observable for Bonjour events
     */
    rx.Observable<BonjourEvent> register(Context context, BonjourService service);

    /**
     * UnRegister a Bonjour service
     *
     * @param context Context of the request
     * @param service Service to resgister
     * @return An Observable for Bonjour events
     */
    rx.Observable<BonjourEvent> unregister(Context context, BonjourService service);
}
