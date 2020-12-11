高并发场景下可以选择 `AtomicLong` 原子类进行计数等操作，除了 `AtomicLong`，在 jdk1.8 中还提供了 `LongAdder`。PS：`AtomicLong` 在 jdk1.5 版本提供。

`AtomicLong` 底层使用 `Unsafe` 的 CAS 实现，当操作失败时，会以自旋的方式重试，直至操作成功。因此在高并发场景下，可能会有很多线程处于重试状态，徒增 CPU 的压力，造成不必要的开销。

`LongAdder` 提供了一个 `base` 值，当竞争小的情况下通过 CAS 更新该值，如果 CAS 操作失败，会初始化一个 `cells` 数组，每个线程都会通过取模的方式定位 `cells` 数组中的一个元素，这样就将操作单个 `AtomicLong value` 的压力分散到数组中的多个元素上。

通过将压力分散，`LongAdder` 可以提供比 `AtomicLong` 更好的性能。获取元素 value 值时，只要将 `base` 与 `cells` 数组中的元素累加即可。

下面是它的原理实现。

```java
    public void increment() {
        add(1L);
    }

    public void add(long x) {
        Cell[] as;
        long b, v;
        // m = as.length -1，取模用，定位数组槽
        int m;
        Cell a;
        // 低竞争条件下，cells 为 null，此时调用 casBase（底层为 CAS 操作，类似 AtomicLong） 方法更新 base
        // PS：cells 数组为懒加载，只有在 CAS 竞争失败的情况下才会初始化
        if ((as = cells) != null || !casBase(b = base, b + x)) {
            boolean uncontended = true;
            // as 数组为 null，或者数组 size = 0，或者计算槽后在数组中不能定位，或者 cell 对象 CAS 操作失败
            if (as == null || (m = as.length - 1) < 0 || (a = as[getProbe() & m]) == null || !(uncontended = a.cas(v = a.value, v + x)))
                longAccumulate(x, null, uncontended);
        }
    }
```

`LongAdder` 继承自 `Striped64`，底层调用 `Striped64.longAccumulate` 方法实现。

 当第一次调用 `add` 方法时，并不会初始化 `cells` 数组，而是通过 CAS 去操作 `base` 值，操作成功后就直接返回了。

 如果 CAS 操作失败，这时会调用 `longAccumulate` 方法，该方法会初始化 `Cell` 类型的数组，后面大部分线程都会直接操作这个数组，但是仍然有部分线程会更新 `base` 值。

`Cell` 元素定义如下：

```java
    @sun.misc.Contended static final class Cell {
        volatile long value;
        Cell(long x) { value = x; }
        final boolean cas(long cmp, long val) {
            return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;
        private static final long valueOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> ak = Cell.class;
                valueOffset = UNSAFE.objectFieldOffset
                    (ak.getDeclaredField("value"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }
```

多个线程操作 `cells` 数组原理如下：

![](https://raw.githubusercontent.com/zhchenme/jdk1.8-source-code-read/master/img/LongAdder1.png)

图片来源 [LongAdder and LongAccumulator in Java](https://www.baeldung.com/java-longadder-and-longaccumulator)

CPU 有多级缓存，这些缓存的最小单位是缓存行(Cache Line)，通常情况下一个缓存行的大小是 64 字节(并不绝对，或者是 64 的倍数)。假设现在要操作一个 long 类型的数组，long 在 Java 中占 64 bit，8 个字节，当操作数组中的一个元素时，会从主存中将该元素的附近的其他元素一起加载进缓存行，即使其他元素你不想操作。

假设两个用 `volatile` 修饰的元素被加载进同一个缓存行，线程 A 更新变量 A 后会将更新后的值刷新回主存，此时缓存行失效，线程 B 再去操作 B 变量只能重新从主存中读取(Cache Miss)。这就造成了伪共享(False sharing)问题。

`Cell` 本身没什么好讲的，仔细看一下，这个类被 `@sun.misc.Contended` 注解修饰，这个注解一般在写业务时用不到，但是它可以解决上面的伪共享问题。

`@sun.misc.Contended` 注解在 jdk1.8 中提供，保证缓存行每次缓存一个变量，剩余的空间用字节来填充。

![](https://raw.githubusercontent.com/zhchenme/jdk1.8-source-code-read/master/img/LongAdder2.png)

图片来源 [LongAdder and LongAccumulator in Java](https://www.baeldung.com/java-longadder-and-longaccumulator)

```java
    final void longAccumulate(long x, LongBinaryOperator fn,
                              boolean wasUncontended) {
        // 线程 threadLocalRandomProbe 属性值
        int h;
        if ((h = getProbe()) == 0) {
            // 初始化 Thread 的 threadLocalRandomProbe 属性值
            ThreadLocalRandom.current(); // force initialization
            h = getProbe();
            wasUncontended = true;
        }
        boolean collide = false;                // True if last slot nonempty
        for (;;) {
            Cell[] as;
            Cell a;
            // cells 数组大小
            int n;
            long v;
            // cells 数组已经初始化
            if ((as = cells) != null && (n = as.length) > 0) {
                // 当前线程对应数组槽的 Cell 对象为空
                if ((a = as[(n - 1) & h]) == null) {
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        // 初始化 Cell，这里存在竞争，可能多个线程都创建了 Cell 对象
                        Cell r = new Cell(x);   // Optimistically create
                        // casCellsBusy() 更新 cellsBusy 为 1，通过 CAS 操作保证只有一个线程操作成功，cellsBusy() 方法相当于一个 spin lock
                        if (cellsBusy == 0 && casCellsBusy()) {
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs; int m, j;
                                if ((rs = cells) != null && (m = rs.length) > 0 && rs[j = (m - 1) & h] == null) {
                                    // rs[j] 对象赋值
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            // Cell 在数组中赋值成功，跳出循环
                            if (created)
                                break;
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                }
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                // CAS 操作成功，跳出循环
                else if (a.cas(v = a.value, ((fn == null) ? v + x : fn.applyAsLong(v, x)))) 
                    break;
                // 数组范围不能大于 JVM 可用核心数，cells != as 表示数组可能扩容
                else if (n >= NCPU || cells != as)
                    collide = false;            // At max size or stale
                // 多个线程出现碰撞，更新 collide = true，出现碰撞后并没有直接扩容 cells 数组，而是重新 rehash，rehash CAS 失败后才会扩容
                else if (!collide) 
                       collide = true;
                // 走到这里说明出现了数组碰撞，且自旋 rehash CAS 失败，这时需要对数组扩容
                else if (cellsBusy == 0 && casCellsBusy()) { 
                    try {
                        // 数组扩容，重置 cells 数组
                        if (cells == as) {      // Expand table unless stale
                            Cell[] rs = new Cell[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            cells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    // 扩容完成后标记碰撞为 false
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                // 重置线程 threadLocalRandomProbe 值，重新 rehash 用
                h = advanceProbe(h);
            }
            else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                boolean init = false;
                try {                           // Initialize table
                    if (cells == as) {
                        // 初始化 cells 数组，默认大小为 2
                        Cell[] rs = new Cell[2];
                        // 角标赋值
                        rs[h & 1] = new Cell(x);
                        cells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init)
                    break;
            }
            // 多个线程初始化，只有一个线程初始化成功，其他线程尝试更新 base 值
            else if (casBase(v = base, ((fn == null) ? v + x :
                                        fn.applyAsLong(v, x))))
                break;                          // Fall back on using base
        }
    }
```

下面是具体每一个分支的详细说明：

 - `cells` 数组不为空，尝试更新数组中的 `Cell` 元素
    - 如果当前线程对应数组中的槽没有 `Cell` 元素，则初始化一个 `Cell` 元素，加锁成功后将初始化的 `Cell` 存在数组对应的槽中，跳出循环，槽位置 = `thread.threadLocalRandomProbe  & cells.length - 1`，这里 `&` 操作相当于 %
    - 如果 `wasUncontended` 为 false，表示 CAS 操作失败，操作失败后会重置线程的 `threadLocalRandomProbe` 属性，自旋时会重新 rehash
    - CAS 操作当前数组槽对应的 `Cell`，累加操作的变量值，累加成功跳出循环，失败重置线程的 `threadLocalRandomProbe` 属性，自旋时会重新 rehash
    - `cells` 数组可能扩容，数组长度不能大于 JVM 的可用核心数，如果扩容，或者数组已经达到最大容量，将 `collide` 值置为 false
       - 这里补充说明一下，`collide` 为碰撞的意思，指的是多个线程经过 hash 后对应数组中的槽是否出现碰撞
       - 如果 `cells` 数组已经扩容到了最大限制，即使出现碰撞也不会再扩容 `cells` 数组了，因此将 `collide` 值置为 false
       - `cells != as` 表示数组出现了扩容，此时忽略碰撞情况，也将 `collide` 值置为 false
    - 如果 `collide` 为 false，将 `collide` 置为 true，意味着这此时已经出现了碰撞，出现碰撞并不会直接扩容 `cells` 数组，而是更新线程 `threadLocalRandomProbe`，自旋时重新 rehash，rehash CAS 失败后才会扩容
    - 如果出现了碰撞，且 rehash 后 CAS 更新 `Cell` 失败，进行加锁，加锁成功对 `cells` 数组扩容
 - `cells` 数组还没有初始化，且线程加锁成功，则初始化 `cells` 数组容量为 2，且将当前线程对应的 value 值封装成 `Cell` 元素，存储 `cells` 数组中
 - 可能有多个线程尝试初始化 `cells` 数组，但最终成功的只有一个，其他初始化失败的并不会以自旋的方式操作 `cells` 数组，而是尝试通过 CAS 去操作 `base` 值，因此在 `cells` 数组初始化完成之后，也是有可能是修改 `base` 值的

到这里 `LongAdder` 的原理就介绍完了，这时再来看以下几个问题？

1. `cells` 数组初始化完成是不是就不会再更新 `base` 值了？
答：不会，可能有多个线程尝试初始化 `cells` 数组，最终只有一个线程成功，失败的线程还会以 CAS 的方式更新 `base` 值

2. `cells` 数组什么时候扩容？
答：多个线程操作 `cells` 数组出现槽碰撞，碰撞后并不会直接扩容，而是修改线程的 `threadLocalRandomProbe` 值，以自旋的方式重新 rehash，如果还出现碰撞(collide = true)，则扩容 `cells` 数组

3. `cells` 数组的最大容量是多少？
答：上面代码过程中有一个 `else if (n >= NCPU || cells != as)` 判断，这个 `NCPU` 表示 JVM 可用核心数，`NCPU = Runtime.getRuntime().availableProcessors();` 。注意这个 JVM 可用核心数并不一定等于 CPU 核心数，比如我的电脑是 6 核，JVM 可用核心数是 12。`else if (n >= NCPU || cells != as)` 意味着数组的容量不能大于 JVM 的可用核心数，假设一个服务器 JVM 可用核心数为 6，由于数组每次扩容 2 倍，第一次初始化时为 2，那最大容量应该为 4。其实不是这样的，因为这个判断是在扩容前进行的，假设此时数组容量为 4，由于可用核心数为 6，条件判断通过，且存在碰撞情况，那么还是会扩容 `cells` 的容量为 8。因此我认为 `cells` 数组的最大容量为第一个大于 JVM 可用核心数的 2 的幂。

如果以上分析有错误分歧，欢迎大家在下面留言交流指正。

参考

[LongAdder and LongAccumulator in Java](https://www.baeldung.com/java-longadder-and-longaccumulator)<br>
[A Guide to False Sharing and @Contended](https://www.baeldung.com/java-false-sharing-contended)<br>
[How do cache lines work?](https://stackoverflow.com/questions/3928995/how-do-cache-lines-work)<br>
[JAVA 拾遗 — CPU Cache 与缓存行](https://www.cnkirito.moe/cache-line/)<br>
[Java并发工具类之LongAdder原理总结](https://github.com/aCoder2013/blog/issues/22)<br>
