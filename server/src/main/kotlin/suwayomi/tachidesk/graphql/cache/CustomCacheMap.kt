package suwayomi.tachidesk.graphql.cache

import org.dataloader.CacheMap
import java.util.concurrent.CompletableFuture

class CustomCacheMap<K : Any, V : Any> : CacheMap<K, V> {
    private val cache: MutableMap<K, CompletableFuture<V>> = HashMap()

    override fun containsKey(key: K): Boolean = cache.containsKey(key)

    override fun get(key: K): CompletableFuture<V> = cache[key]!!

    fun getKeys(): Collection<K> = cache.keys.toSet()

    override fun getAll(): Collection<CompletableFuture<V>> = cache.values

    override fun putIfAbsentAtomically(
        key: K,
        value: CompletableFuture<V>,
    ): CompletableFuture<V> {
        cache[key] = value
        return value
    }

    override fun delete(key: K): CacheMap<K, V> {
        cache.remove(key)
        return this
    }

    override fun clear(): CacheMap<K, V> {
        cache.clear()
        return this
    }

    override fun size(): Int = cache.size
}
