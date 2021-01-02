package com.github.pwittchen.reactivenetwork.library

import android.content.Context
import rx.Observable

/**
 * Created by nulldev on 12/29/16.
 */

class ReactiveNetwork {
    companion object {
        fun observeNetworkConnectivity(context: Context) = Observable.just(Connectivity())!!
    }
}
