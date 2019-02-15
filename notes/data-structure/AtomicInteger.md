`AtomicInteger` 底层通过 `volatile` 和 CAS 来保证线程安全，关于这两个概念就不赘述了，来简单的分析一下源码，看一些内部实现细节。

### AtomicInteger 原理

以 `AtomicInteger` 的 `getAndIncrement` 方法为例：

``` java
    public final int getAndIncrement() {
        return unsafe.getAndAddInt(this, valueOffset, 1);
    }
```

这个 `valueOffset` 是什么呢？接着看代码：

``` java
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long valueOffset;

    static {
        try {
            valueOffset = unsafe.objectFieldOffset
                (AtomicInteger.class.getDeclaredField("value"));
        } catch (Exception ex) { throw new Error(ex); }
    }
```

`valueOffset` 表示 value 字段在内存中的偏移量，可以用这个偏移量获取内存中的 value 值。

接下来看 `getAndIncrement` 调用的 `unsafe.getAndAddInt` 方法：

``` java
    /**
     * @param var1 当前 AtomicInteger 对象的引用
     * @param var2 value 在内存中的偏移量
     * @param var4 增加的值
     */
    public final int getAndAddInt(Object var1, long var2, int var4) {
        int var5;
        // 当预期值与内存值相等时结束循环
        do {
            // 获取内存中的 value，当作预期值进行循环判断
            var5 = this.getIntVolatile(var1, var2);
            /**
             * @param var5        预期值
             * @param var5 + var4 更新值
             */
        } while(!this.compareAndSwapInt(var1, var2, var5, var5 + var4));

        return var5;
    }
```

看了上面的代码就比较明了了，在更新值之前都会先获取内存中的值，把该内存值当作预期值进行比较。假如有线程把内存中的值修改了，那么预期值与内存值就会不一致，接着继续进行循环判断，直到内存值与预期值一致时才更新内存值，这样就能保证原子性。

那么 CAS 底层又是怎么保证原子操作的呢，具体可以移步参考下面的文章：<br>
[https://www.jianshu.com/p/bd68ddf91240](https://www.jianshu.com/p/bd68ddf91240)<br>
[https://blog.csdn.net/v123411739/article/details/79561458](https://blog.csdn.net/v123411739/article/details/79561458)


