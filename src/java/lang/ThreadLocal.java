/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.lang;
import java.lang.ref.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * This class provides thread-local variables.  These variables differ from
 * their normal counterparts in that each thread that accesses one (via its
 * {@code get} or {@code set} method) has its own, independently initialized
 * copy of the variable.  {@code ThreadLocal} instances are typically private
 * static fields in classes that wish to associate state with a thread (e.g.,
 * a user ID or Transaction ID).
 *
 * <p>For example, the class below generates unique identifiers local to each
 * thread.
 * A thread's id is assigned the first time it invokes {@code ThreadId.get()}
 * and remains unchanged on subsequent calls.
 * <pre>
 * import java.util.concurrent.atomic.AtomicInteger;
 *
 * public class ThreadId {
 *     // Atomic integer containing the next thread ID to be assigned
 *     private static final AtomicInteger nextId = new AtomicInteger(0);
 *
 *     // Thread local variable containing each thread's ID
 *     private static final ThreadLocal&lt;Integer&gt; threadId =
 *         new ThreadLocal&lt;Integer&gt;() {
 *             &#64;Override protected Integer initialValue() {
 *                 return nextId.getAndIncrement();
 *         }
 *     };
 *
 *     // Returns the current thread's unique ID, assigning it if necessary
 *     public static int get() {
 *         return threadId.get();
 *     }
 * }
 * </pre>
 * <p>Each thread holds an implicit reference to its copy of a thread-local
 * variable as long as the thread is alive and the {@code ThreadLocal}
 * instance is accessible; after a thread goes away, all of its copies of
 * thread-local instances are subject to garbage collection (unless other
 * references to these copies exist).
 *
 * @author  Josh Bloch and Doug Lea
 * @since   1.2
 */
public class ThreadLocal<T> {
    /**
     * ThreadLocals rely on per-thread linear-probe hash maps attached
     * to each thread (Thread.threadLocals and
     * inheritableThreadLocals).  The ThreadLocal objects act as keys,
     * searched via threadLocalHashCode.  This is a custom hash code
     * (useful only within ThreadLocalMaps) that eliminates collisions
     * in the common case where consecutively constructed ThreadLocals
     * are used by the same threads, while remaining well-behaved in
     * less common cases.
     *
     * ThreadLocal 的哈希值并不是通过 ThreadLocal.hashCode 计算，而是通过一个原子类实现
     * TODO 这个值 set 与 get 时是否不一致？如果不一致那 get 的时候是怎么定位到的呢
     */
    private final int threadLocalHashCode = nextHashCode();

    /**
     * The next hash code to be given out. Updated atomically. Starts at
     * zero.
     */
    private static AtomicInteger nextHashCode =
        new AtomicInteger();

    /**
     * The difference between successively generated hash codes - turns
     * implicit sequential thread-local IDs into near-optimally spread
     * multiplicative hash values for power-of-two-sized tables.
     *
     * 该值生成出来的值可以较为均匀地分布在 2 的幂大小的数组中
     * 据说与斐波那契散列有关...
     */
    private static final int HASH_INCREMENT = 0x61c88647;

    /**
     * Returns the next hash code.
     *
     * 哈希值自增，因此可以均匀落在哈希表桶位置上
     */
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    /**
     * Returns the current thread's "initial value" for this
     * thread-local variable.  This method will be invoked the first
     * time a thread accesses the variable with the {@link #get}
     * method, unless the thread previously invoked the {@link #set}
     * method, in which case the {@code initialValue} method will not
     * be invoked for the thread.  Normally, this method is invoked at
     * most once per thread, but it may be invoked again in case of
     * subsequent invocations of {@link #remove} followed by {@link #get}.
     *
     * <p>This implementation simply returns {@code null}; if the
     * programmer desires thread-local variables to have an initial
     * value other than {@code null}, {@code ThreadLocal} must be
     * subclassed, and this method overridden.  Typically, an
     * anonymous inner class will be used.
     *
     * @return the initial value for this thread-local
     */
    protected T initialValue() {
        return null;
    }

    /**
     * Creates a thread local variable. The initial value of the variable is
     * determined by invoking the {@code get} method on the {@code Supplier}.
     *
     * @param <S> the type of the thread local's value
     * @param supplier the supplier to be used to determine the initial value
     * @return a new thread local variable
     * @throws NullPointerException if the specified supplier is null
     * @since 1.8
     */
    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        return new SuppliedThreadLocal<>(supplier);
    }

    /**
     * Creates a thread local variable.
     * @see #withInitial(java.util.function.Supplier)
     */
    public ThreadLocal() {
    }

    /**
     * Returns the value in the current thread's copy of this
     * thread-local variable.  If the variable has no value for the
     * current thread, it is first initialized to the value returned
     * by an invocation of the {@link #initialValue} method.
     *
     * 返回当前线程返回的 value
     *
     * @return the current thread's value of this thread-local
     */
    public T get() {
        Thread t = Thread.currentThread();
        // 根据当前线程获取对应的 map
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            // 根据当前对象获取到对应的 Entry
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                // 返回 Entry 中对应的 value
                return result;
            }
        }
        // map 为空时创建
        return setInitialValue();
    }

    /**
     * Variant of set() to establish initialValue. Used instead
     * of set() in case user has overridden the set() method.
     *
     * @return the initial value
     */
    private T setInitialValue() {
        // 获取 initialValue() 方法中对应的 value
        T value = initialValue();
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        // 如果对应的 map 不为空，则重置对应的 value
        if (map != null)
            map.set(this, value);
        // map 为空，初始化 map
        else
            createMap(t, value);
        return value;
    }

    /**
     * Sets the current thread's copy of this thread-local variable
     * to the specified value.  Most subclasses will have no need to
     * override this method, relying solely on the {@link #initialValue}
     * method to set the values of thread-locals.
     *
     * 设置 value
     *
     * @param value the value to be stored in the current thread's copy of
     *        this thread-local.
     */
    public void set(T value) {
        Thread t = Thread.currentThread();
        // 根据当前线程获取对应的 map
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
    }

    /**
     * Removes the current thread's value for this thread-local
     * variable.  If this thread-local variable is subsequently
     * {@linkplain #get read} by the current thread, its value will be
     * reinitialized by invoking its {@link #initialValue} method,
     * unless its value is {@linkplain #set set} by the current thread
     * in the interim.  This may result in multiple invocations of the
     * {@code initialValue} method in the current thread.
     *
     * 移除当前线程的 ThreadLocalMap
     *
     * @since 1.5
     */
     public void remove() {
         ThreadLocalMap m = getMap(Thread.currentThread());
         if (m != null)
             m.remove(this);
     }

    /**
     * Get the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * 返回当前线程对应的 ThreadLocalMap
     *
     * @param  t the current thread
     * @return the map
     */
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }

    /**
     * Create the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * 为当前线程创建 ThreadLocalMap
     *
     * @param t the current thread
     * @param firstValue value for the initial entry of the map
     */
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }

    /**
     * Factory method to create map of inherited thread locals.
     * Designed to be called only from Thread constructor.
     *
     * @param  parentMap the map associated with parent thread
     * @return a map containing the parent's inheritable bindings
     */
    static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
        return new ThreadLocalMap(parentMap);
    }

    /**
     * Method childValue is visibly defined in subclass
     * InheritableThreadLocal, but is internally defined here for the
     * sake of providing createInheritedMap factory method without
     * needing to subclass the map class in InheritableThreadLocal.
     * This technique is preferable to the alternative of embedding
     * instanceof tests in methods.
     */
    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * An extension of ThreadLocal that obtains its initial value from
     * the specified {@code Supplier}.
     */
    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        protected T initialValue() {
            return supplier.get();
        }
    }

    /**
     * ThreadLocalMap is a customized hash map suitable only for
     * maintaining thread local values. No operations are exported
     * outside of the ThreadLocal class. The class is package private to
     * allow declaration of fields in class Thread.  To help deal with
     * very large and long-lived usages, the hash table entries use
     * WeakReferences for keys. However, since reference queues are not
     * used, stale entries are guaranteed to be removed only when
     * the table starts running out of space.
     */
    static class ThreadLocalMap {

        /**
         * The entries in this hash map extend WeakReference, using
         * its main ref field as the key (which is always a
         * ThreadLocal object).  Note that null keys (i.e. entry.get()
         * == null) mean that the key is no longer referenced, so the
         * entry can be expunged from table.  Such entries are referred to
         * as "stale entries" in the code that follows.
         */
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /** The value associated with this ThreadLocal. */
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                // key 为弱引用
                super(k);
                value = v;
            }
        }

        /**
         * The initial capacity -- MUST be a power of two.
         *
         * 哈比表数组默认初始化大小
         */
        private static final int INITIAL_CAPACITY = 16;

        /**
         * The table, resized as necessary.
         * table.length MUST always be a power of two.
         *
         * 底层哈希表数组
         */
        private Entry[] table;

        /**
         * The number of entries in the table.
         */
        private int size = 0;

        /**
         * The next size value at which to resize.
         *
         * 扩容阈值
         */
        private int threshold; // Default to 0

        /**
         * Set the resize threshold to maintain at worst a 2/3 load factor.
         */
        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        /**
         * Increment i modulo len.
         */
        private static int nextIndex(int i, int len) {
            return ((i + 1 < len) ? i + 1 : 0);
        }

        /**
         * Decrement i modulo len.
         */
        private static int prevIndex(int i, int len) {
            return ((i - 1 >= 0) ? i - 1 : len - 1);
        }

        /**
         * Construct a new map initially containing (firstKey, firstValue).
         * ThreadLocalMaps are constructed lazily, so we only create
         * one when we have at least one entry to put in it.
         *
         * 第一次添加的时候会调用构造函数进行初始化，并设置第一个线程对应的 key 与 value
         */
        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
            // 初始化哈希表数组
            table = new Entry[INITIAL_CAPACITY];
            // 计算桶位置
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            // 设置到对应的桶位置上（已经有了一个 key 与 value）
            table[i] = new Entry(firstKey, firstValue);
            // 初始化 size 为 1
            size = 1;
            // 设置扩容阈值为初始容量的 2/3
            setThreshold(INITIAL_CAPACITY);
        }

        /**
         * Construct a new map including all Inheritable ThreadLocals
         * from given parent map. Called only by createInheritedMap.
         *
         * @param parentMap the map associated with parent thread.
         */
        private ThreadLocalMap(ThreadLocalMap parentMap) {
            Entry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new Entry[len];

            for (int j = 0; j < len; j++) {
                Entry e = parentTable[j];
                if (e != null) {
                    @SuppressWarnings("unchecked")
                    ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        Entry c = new Entry(key, value);
                        int h = key.threadLocalHashCode & (len - 1);
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = c;
                        size++;
                    }
                }
            }
        }

        /**
         * Get the entry associated with key.  This method
         * itself handles only the fast path: a direct hit of existing
         * key. It otherwise relays to getEntryAfterMiss.  This is
         * designed to maximize performance for direct hits, in part
         * by making this method readily inlinable.
         *
         * 根据 key 获取对应的 value
         *
         * @param  key the thread local object
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntry(ThreadLocal<?> key) {
            // 获取桶位置
            int i = key.threadLocalHashCode & (table.length - 1);
            // 获取桶位置上对应的 entry
            Entry e = table[i];
            // 哈希不冲突，直接获取对应的 value 并返回
            if (e != null && e.get() == key)
                return e;
            // 哈希冲突，则遍历后面的桶位置，进行查找，当然 key 可能因为是弱引用被擦出，需要额外处理
            else
                return getEntryAfterMiss(key, i, e);
        }

        /**
         * Version of getEntry method for use when key is not found in
         * its direct hash slot.
         *
         * 哈希冲突
         *
         * @param  key the thread local object
         * @param  i the table index for key's hash code
         * @param  e the entry at table[i]
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
            Entry[] tab = table;
            int len = tab.length;

            while (e != null) {
                ThreadLocal<?> k = e.get();
                // 找到直接返回， value
                if (k == key)
                    return e;
                // 如果 key 被擦出，则清除
                if (k == null)
                    expungeStaleEntry(i);
                else
                    // 重置 i 用于循环遍历
                    i = nextIndex(i, len);
                e = tab[i];
            }
            // 没有找到返回 null
            return null;
        }

        /**
         * Set the value associated with key.
         *
         * 设置键值对
         *
         * @param key the thread local object
         * @param value the value to be set
         */
        private void set(ThreadLocal<?> key, Object value) {

            // We don't use a fast path as with get() because it is at
            // least as common to use set() to create new entries as
            // it is to replace existing ones, in which case, a fast
            // path would fail more often than not.

            Entry[] tab = table;
            int len = tab.length;
            // 计算 key 对应的桶位置
            int i = key.threadLocalHashCode & (len-1);

            // e = tab[i = nextIndex(i, len)] 线性探测法解决哈希冲突
            for (Entry e = tab[i]; e != null; e = tab[i = nextIndex(i, len)]) {
                ThreadLocal<?> k = e.get();

                // key 重复，value 覆盖
                if (k == key) {
                    e.value = value;
                    return;
                }

                // key 为 null，是因为 key 是弱引用，可能已经被 GC 回收了
                if (k == null) {
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }

            // 找到插入的位置，存储 key 与 value
            tab[i] = new Entry(key, value);
            int sz = ++size;
            // cleanSomeSlots 用于删除可能已经被 GC 回收的 key
            // 如果没有 key 被 GC 回收，并且哈希表数组中的键值对数量大于 2/3，执行扩容操作
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

        /**
         * Remove the entry for key.
         *
         * 根据 key 移除对应的键值对
         */
        private void remove(ThreadLocal<?> key) {
            Entry[] tab = table;
            int len = tab.length;
            // 计算出对应的桶位置，当然对应桶位置上的键值对并不一定是当前 key 对应的键值对，因为可能存在哈希冲突
            int i = key.threadLocalHashCode & (len-1);
            // 从 i 位置向后遍历，遍历结束的位置是后续桶位置上为 null
            for (Entry e = tab[i]; e != null; e = tab[i = nextIndex(i, len)]) {
                if (e.get() == key) {
                    // key 清空
                    e.clear();
                    // 调用 expungeStaleEntry 方法
                    expungeStaleEntry(i);
                    return;
                }
            }
        }

        /**
         * Replace a stale entry encountered during a set operation
         * with an entry for the specified key.  The value passed in
         * the value parameter is stored in the entry, whether or not
         * an entry already exists for the specified key.
         *
         * As a side effect, this method expunges all stale entries in the
         * "run" containing the stale entry.  (A run is a sequence of entries
         * between two null slots.)
         *
         * @param  key the key
         * @param  value the value to be associated with key
         * @param  staleSlot index of the first stale entry encountered while
         *         searching for key. 已经被擦除的 key 对应的桶位置
         */
        // TODO
        private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                                       int staleSlot) {
            // 获取哈希表数组
            Entry[] tab = table;
            int len = tab.length;
            Entry e;

            // Back up to check for prior stale entry in current run.
            // We clean out whole runs at a time to avoid continual
            // incremental rehashing due to garbage collector freeing
            // up refs in bunches (i.e., whenever the collector runs).
            // 记录 key 被擦除的桶位置（为 staleSlot 位置前的第一个连续的 key 被擦除的索引
            // 或 staleSlot 位置后第一个连续的 key 被擦除或 key 重复的索引）
            int slotToExpunge = staleSlot;

            // 寻找 staleSlot 前一个连续不为 null 的 key 被擦除的桶位置
            for (int i = prevIndex(staleSlot, len); (e = tab[i]) != null; i = prevIndex(i, len))
                if (e.get() == null)
                    slotToExpunge = i;

            // Find either the key or trailing null slot of run, whichever
            // occurs first
            // 寻找 staleSlot 后一个不为 null 的 entry
            for (int i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();

                // If we find key, then we need to swap it
                // with the stale entry to maintain hash table order.
                // The newly stale slot, or any other stale slot
                // encountered above it, can then be sent to expungeStaleEntry
                // to remove or rehash all of the other entries in run.
                // key 重复，这里 key 是要插入的 key
                if (k == key) {
                    e.value = value;

                    // i 位置与 staleSlot 位置的 entry 互换，因为 staleSlot 位置上的 key 已经被回收，没有意义了
                    // TODO 那为什么不把 key 被 GC 回收的 entry 置为 null 而是位置互换呢？不要急，下面 expungeStaleEntry 方法会做
                    tab[i] = tab[staleSlot];
                    tab[staleSlot] = e;

                    // Start expunge at preceding stale entry if it exists
                    // slotToExpunge == staleSlot 意味着向前没有查找到连续的键值对 key 被擦除的情况
                    if (slotToExpunge == staleSlot)
                        slotToExpunge = i;
                    // 将 slotToExpunge 位置上的 entry 清除
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }

                // If we didn't find stale entry on backward scan, the
                // first stale entry seen while scanning for key is the
                // first still present in the run.
                // 如果 i 位置上的 key 也已经被擦除将 slotToExpunge 置为 i
                if (k == null && slotToExpunge == staleSlot)
                    slotToExpunge = i;
            }

            // If key not found, put new entry in stale slot
            // 把新的键值对直接存储在 staleSlot 位置
            tab[staleSlot].value = null;
            tab[staleSlot] = new Entry(key, value);

            // If there are any other stale entries in run, expunge them
            // 如果向前或向后找到了 key 被擦除的 entry，则清除 slotToExpunge 位置上的键值对
            if (slotToExpunge != staleSlot)
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }

        /**
         * Expunge a stale entry by rehashing any possibly colliding entries
         * lying between staleSlot and the next null slot.  This also expunges
         * any other stale entries encountered before the trailing null.  See
         * Knuth, Section 6.4
         *
         * 移除 staleSlot 桶位置上的键值对，移除后需要考虑之前是否有哈希冲突，如果有，则额外处理
         * 如何解决之前可能存在哈希冲突呢，答案就是把后面连续的元素重新 rehash，然后重新放置，太聪明了...
         *
         * 这是一个核心方法，需重点理解
         *
         * @param staleSlot index of slot known to have null key key 被擦除的键值对的索引
         * @return the index of the next null slot after staleSlot
         * (all between staleSlot and this slot will have been checked
         * for expunging).
         */
        private int expungeStaleEntry(int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;

            // expunge entry at staleSlot
            // value 置 null，对应桶位置上的 Entry 也置 null
            tab[staleSlot].value = null;
            tab[staleSlot] = null;
            // 键值对数量减 1
            size--;

            // Rehash until we encounter null
            Entry e;
            int i;
            // 删除一个 key 被擦除的键值对，可能因为之前哈希冲突，导致后面桶位置上的键值对位置不准确，因此要向前调整后面桶位置上的键值对
            // 从 staleSlot 位置向后遍历，要求必须连续，与 IdentityHashMap 一致 
            for (i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();
                // 如果后面桶位置上键值对被擦除，则直接清除，因此 expungeStaleEntry 方法并不是只清除 staleSlot 位置上的键值对
                if (k == null) {
                    e.value = null;
                    tab[i] = null;
                    size--;
                } else {
                    // 并不是向前移动，而是重新 rehash，计算对应的桶位置
                    // TODO 重点理解，重新 rehash 解决之前可能存在哈希冲突的情况
                    // 并不以哈希冲突为条件，即使 NULL 条件前有元素不与该元素有哈希冲突也会重新 rehash
                    int h = k.threadLocalHashCode & (len - 1);
                    if (h != i) {
                        tab[i] = null;

                        // Unlike Knuth 6.4 Algorithm R, we must scan until
                        // null because multiple entries could have been stale.
                        while (tab[h] != null)
                            h = nextIndex(h, len);
                        tab[h] = e;
                    }
                }
            }
            // 返回 staleSlot 之后第一个键值对为 null 的桶位置
            return i;
        }

        /**
         * Heuristically scan some cells looking for stale entries.
         * This is invoked when either a new element is added, or
         * another stale one has been expunged. It performs a
         * logarithmic number of scans, as a balance between no
         * scanning (fast but retains garbage) and a number of scans
         * proportional to number of elements, that would find all
         * garbage but would cause some insertions to take O(n) time.
         *
         * 清除哈希表中 key 已经被擦出的键值对
         * 注意：执行的是对数扫描，并不会扫描整个哈希表数组
         *
         * @param i a position known NOT to hold a stale entry. The
         * scan starts at the element after i.
         *
         * @param n scan control: {@code log2(n)} cells are scanned,
         * unless a stale entry is found, in which case
         * {@code log2(table.length)-1} additional cells are scanned.
         * When called from insertions, this parameter is the number
         * of elements, but when from replaceStaleEntry, it is the
         * table length. (Note: all this could be changed to be either
         * more or less aggressive by weighting n instead of just
         * using straight log n. But this version is simple, fast, and
         * seems to work well.)
         *
         * @return true if any stale entries have been removed.
         */
        private boolean cleanSomeSlots(int i, int n) {
            boolean removed = false;
            Entry[] tab = table;
            int len = tab.length;
            do {
                i = nextIndex(i, len);
                // 获取对应桶位置上的 Entry
                Entry e = tab[i];
                // Entry 不为 null，key 为 null 是因为 key 是弱引用可能会被 GC 回收，因此需要在哈希表中删除
                if (e != null && e.get() == null) {
                    n = len;
                    // 如果有键值对被擦出就返回 true
                    removed = true;
                    // 删除 i 位置上的键值对
                    i = expungeStaleEntry(i);
                }
            } /* 对数扫描，并不会扫描整个哈希表数组 */while ( (n >>>= 1) != 0);
            return removed;
        }

        /**
         * Re-pack and/or re-size the table. First scan the entire
         * table removing stale entries. If this doesn't sufficiently
         * shrink the size of the table, double the table size.
         */
        private void rehash() {
            // 清除所有 key 被擦出的键值对
            expungeStaleEntries();

            // Use lower threshold for doubling to avoid hysteresis
            // 再次判断
            // TODO Q：这个判断有什么作用？不可能是 false 的啊
            // A：因为上面调用了 expungeStaleEntries 方法，可能有的键值对被移除导致哈希表数组的键值对非常少，此时就没有扩容的必要了
            if (size >= threshold - threshold / 4)
                resize();
        }

        /**
         * Double the capacity of the table.
         *
         * 扩容机制
         */
        private void resize() {
            Entry[] oldTab = table;
            int oldLen = oldTab.length;
            // 新哈希表的大小为原哈希表大小的 2 倍
            int newLen = oldLen * 2;
            // 初始化新哈希表
            Entry[] newTab = new Entry[newLen];
            // 记录新哈希表中键值对的个数
            int count = 0;

            // 遍历老哈希表数组，尽心 rehash
            for (int j = 0; j < oldLen; ++j) {
                // 获取老哈希表桶位置上的 Entry
                Entry e = oldTab[j];
                if (e != null) {
                    ThreadLocal<?> k = e.get();
                    // 如果 key 被回收，则把 value 也置 null
                    // 无时不刻判断着 key 被擦除的情况
                    if (k == null) {
                        e.value = null; // Help the GC
                    } else {
                        // 计算老哈希表中的键值对在新哈希表中的桶位置
                        int h = k.threadLocalHashCode & (newLen - 1);
                        // 这里也可能会产生哈希冲突
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);
                        newTab[h] = e;
                        count++;
                    }
                }
            }

            // 设置新的扩容阈值，2/3
            setThreshold(newLen);
            size = count;
            // 新的哈希表替代老的哈希表
            table = newTab;
        }

        /**
         * Expunge all stale entries in the table.
         *
         * 扫描整个哈希表数组，清空所有 key 被擦除的键值对
         */
        private void expungeStaleEntries() {
            Entry[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                Entry e = tab[j];
                if (e != null && e.get() == null)
                    expungeStaleEntry(j);
            }
        }
    }
}
