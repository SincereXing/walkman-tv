package com.walkman.tv.di

import java.net.Inet4Address
import java.net.NetworkInterface

/** First non-loopback IPv4 address on any active interface — the address the phone scans. */
fun getLanIp(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
        ?.hostAddress
}.getOrNull()
