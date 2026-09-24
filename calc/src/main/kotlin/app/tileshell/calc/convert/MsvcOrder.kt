package app.tileshell.calc.convert

/**
 * The two MSVC STL behaviours that decide the order of CalcManager's "About equal to" list when values tie (zero or
 * negative inputs in Power, equal factors such as Milliliters / Cubic centimeters). Taken from microsoft/STL
 * stl/inc/xhash and xutility.
 */
internal object MsvcOrder {

    /**
     * Iteration order of `CalculateSuggested`'s `unordered_map<Unit, ConversionData, UnitHash>` (hash = unit id).
     * The interop bridge builds the map by inserting [keys] in `LoadOrderedRatios` order; `CalculateSuggested` then
     * copy-constructs it, and the copy constructor re-inserts in the source's iteration order into the same bucket
     * count. MSVC keeps one list whose buckets are contiguous ranges: a key whose bucket is empty is appended, a new
     * key in a non-empty bucket goes before the bucket's first element (`_Find_last` walks back to `_Bucket_lo`), the
     * table starts at 8 buckets with max load factor 1.0 and grows 8x below 512 buckets, and a rehash re-files the list
     * in order under the same rule (`_Forced_rehash`).
     */
    fun unorderedMapIterationOrderAfterCopy(keys: List<Int>): List<Int> {
        var buckets = 8
        var list = ArrayList<Int>()
        for (key in keys) {
            val newSize = list.size + 1
            if (newSize.toFloat() / buckets.toFloat() > 1.0f) {
                buckets = grownBucketCount(buckets, newSize)
                list = refile(list, buckets)
            }
            insert(list, key, buckets)
        }
        return refile(list, buckets)
    }

    private fun grownBucketCount(old: Int, forSize: Int): Int {
        val required = maxOf(8, forSize)
        if (old >= required) return old
        if (old < 512 && old * 8 >= required) return old * 8
        var pow = 1
        while (pow < required) pow = pow shl 1
        return pow
    }

    private fun refile(list: List<Int>, buckets: Int): ArrayList<Int> {
        val out = ArrayList<Int>(list.size)
        for (key in list) insert(out, key, buckets)
        return out
    }

    private fun insert(list: ArrayList<Int>, key: Int, buckets: Int) {
        val mask = buckets - 1
        val bucketLo = list.indexOfFirst { (it and mask) == (key and mask) }
        if (bucketLo < 0) list.add(key) else list.add(bucketLo, key)
    }

    /**
     * MSVC `std::sort` below its `_ISORT_MAX` of 32 elements is this insertion sort (`_Insertion_sort_unchecked`), so
     * equal elements — and NaN magnitudes, which compare false both ways — keep their input order. Only Data can pass
     * 32 suggestions (33); its values never tie (distinct factors, zeros are dropped), so any correct sort, including
     * MSVC's introsort, gives the same order there.
     */
    fun <T> insertionSort(items: MutableList<T>, less: (T, T) -> Boolean) {
        for (mid in 1 until items.size) {
            val value = items[mid]
            if (less(value, items[0])) {
                for (k in mid downTo 1) items[k] = items[k - 1]
                items[0] = value
            } else {
                var hole = mid
                while (less(value, items[hole - 1])) {
                    items[hole] = items[hole - 1]
                    hole--
                }
                items[hole] = value
            }
        }
    }
}
