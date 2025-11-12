package org.ireader.coreapi.http

/**
 * Stub implementations for IReader's HTTP clients
 * IReader extensions expect these but we'll provide minimal implementations
 */
class HttpClients : HttpClientsInterface {
    override val browser: BrowserEngine = BrowserEngine()
    override val default: HttpClient = HttpClient()
    override val cloudflareClient: HttpClient = HttpClient()
}

interface HttpClientsInterface {
    val browser: BrowserEngine
    val default: HttpClient
    val cloudflareClient: HttpClient
}

/**
 * Stub HttpClient - IReader extensions will use their own HTTP implementation
 */
class HttpClient

/**
 * Stub BrowserEngine
 */
class BrowserEngine
