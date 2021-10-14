package info.skyblond.ariteg.proto

import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.http.apache.ProxyConfiguration
import java.net.URI

fun getProxyApacheClientBuilder(): ApacheHttpClient.Builder {
    return ApacheHttpClient.builder()
        .proxyConfiguration(
            ProxyConfiguration.builder()
                .endpoint(URI.create("http://127.0.0.1:1081"))
                .build()
        )
}


