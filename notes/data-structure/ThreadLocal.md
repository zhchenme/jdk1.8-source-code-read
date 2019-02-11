### 一、ThreadLocal 简介

在学习源码之前，有一个概念我们需要先明白：`ThreadLocal` 可以使多线程间数据读写隔离，因此 `ThreadLocal` 解决的是线程局部变量安全性问题，并不是多线程间共享变量安全性问题。

`ThreadLocal` 在使用时必须先初始化 value，否则会报空指针异常，你可以通过 `set` 方法与重写 `initialValue` 方法两种方式初始化 value。

下面是 `ThreadLocal` 原理图，读源码的时候可以参考。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20190211184127434.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NvZGVqYXM=,size_16,color_FFFFFF,t_70)

### 二、ThreadLocal 源码

我们先来了解一下 `ThreadLocal`，然后再逐渐了解 `ThreadLocalMap`。

**2.1 内部相关属性**

``` java
    /*
     * ThreadLocal 的哈希值通过一个原子类计算
     */
    private final int threadLocalHashCode = nextHashCode();
    /**
     * 用于计算 ThreadLocal 哈希值的原子类
     */
    private static AtomicInteger nextHashCode = new AtomicInteger();
    /** 
     * 计算 ThreadLocal 哈希值的魔数 
     * 该值生成出来的值可以较为均匀地分布在 2 的幂大小的数组中
     * 据说与斐波那契散列有关...
     */
    private static final int HASH_INCREMENT = 0x61c88647;
```

`ThreadLocalMap` 的结构是通过纯数组实现的，因此 `ThreadLocal` 计算哈希值的方式也比较特殊，通过 `nextHashCode()` 方法生成哈希值，下面是具体实现。

``` java
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    } 
```
生成哈希值时每次加上 `0x61c88647`，据了解通过 `0x61c88647` 计算出来的哈希值能够均匀的分布在 2 的幂大小的数组中，有兴趣的可以网上查一下进行详细的了解。

**2.2 set 方法**

``` java
    public void set(T value) {
        Thread t = Thread.currentThread();
        // 根据当前线程获取对应的 map
        ThreadLocalMap map = getMap(t);
        if (map != null)
            // key 是当前 ThreadLocal 对象的引用
            map.set(this, value);
        else
            createMap(t, value);
    }
```

在设置 value 时会先调用 `getMap` 方法根据当前线程获取对应的 map，如果 map 存在就设置值，不存在则创建 map，下面跟别来看下对应的方法（`map.set` 方法会在下面分析）。

``` java
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }
```

`getMap` 方法很简单，就是返回当前线程的 `threadLocals`，这个 `threadLocals` 就是 `ThreadLocalMap` 对象。由此可以知道每个 `Thread` 内部都有一个 `ThreadLocalMap` 变量。

``` java
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }
```

`createMap` 方法也比较简单，创建一个 `ThreadLocalMap` 并赋值给当前线程的 `threadLocals` 变量。

**2.3 get 方法**

``` java
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
```

如果 map 存在的话会先获取到当前线程对应的 map，然后根据当前 `ThreadLocal` 的弱引用获取 `Entry`，最终返回 `Entry` 中的 value 即可。如果 map 不存在则调用 `setInitialValue` 方法创建，下面是具体实现细节。

``` java
    private T setInitialValue() {
        // 获取 initialValue() 方法中对应的 value，
        // 如果没有重写 initialValue 方法会抛空指针异常
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
```

**2.4 remove 方法**

``` java
     public void remove() {
         ThreadLocalMap m = getMap(Thread.currentThread());
         if (m != null)
             m.remove(this);
     }
```

`remove` 方法调用了 `ThreadLocalMap` 中的 `remove` 方法删除当前线程的，这个方法到下面介绍 `ThreadLocalMap` 时再详细分析。

### 三、ThreadLocalMap 源码

`ThreadLocal` 源码中最有意思的就属 `ThreadLocalMap` 了，它到底有哪些巧妙的设计呢？下面就来一探究竟吧。

**3.1 内部相关属性**

``` java
        /**
         * 哈比表数组默认初始化大小
         */
        private static final int INITIAL_CAPACITY = 16;
        /**
         * 底层哈希表数组
         */
        private Entry[] table;
        /**
         * 哈希表键值对个数
         */
        private int size = 0;
        /**
         * 扩容阈值
         */
        private int threshold; // Default to 0
        /**
         * 设置扩容阈值为容量的 2/3
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
```

**3.2 构造函数**

我们来看其中一个构造函数。

``` java
        /**
         * 第一次添加的时候会调用该构造函数进行初始化，并设置第一个线程对应的 key 与 value
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
```

**3.3 Entry 结构**

``` java
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /** The value associated with this ThreadLocal. */
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                // key 为弱引用
                super(k);
                value = v;
            }
        }
```

`ThreadLocalMap` 中存储键值对的结构是 `Entry`，`Entry` 实现了 `WeakReference` 类使 key 成为一个弱引用。

// todo 弱引用





