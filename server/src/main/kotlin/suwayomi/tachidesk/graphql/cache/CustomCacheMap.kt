package suwayomi.tachidesk.graphql.cache

import org.dataloader.CacheMap
import java.util.concurrent.CompletableFuture

class CustomCacheMap<K, V> : CacheMap<K, V> {
    private val cache: MutableMap<K, CompletableFuture<V>>

    init {
        cache = HashMap()
    }

    override fun containsKey(key: K): Boolean = cache.containsKey(key)

    override fun get(key: K): CompletableFuture<V> = cache[key]!!

    fun getKeys(): Collection<K> = cache.keys.toSet()

    override fun getAll(): Collection<CompletableFuture<V>> = cache.values

    override fun set(
        key: K,
        value: CompletableFuture<V>,
    ): CacheMap<K, V> {
        cache[key] = value
        return this
    }

    override fun delete(key: K): CacheMap<K, V> {
        cache.remove(key)
        return this
    }

    override fun clear(): CacheMap<K, V> {
        cache.clear()
        return this
    }
}
