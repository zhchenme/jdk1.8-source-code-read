高并发场景下可以选择 `AtomicLong` 原子类进行计数等操作，除了 `AtomicLong`，在 jdk1.8 中还提供了 `LongAdder`。PS：`AtomicLong` 在 jdk1.5 版本提供。

`AtomicLong` 底层使用 `Unsafe` 的 CAS 实现，当操作失败时，会以自旋的方式重试，直至操作成功。因此在高并发场景下，可能会有很多线程处于重试状态，徒增 CPU 的压力，造成不必要的开销。

`LongAdder` 提供了一个 `base` 值，当竞争小的情况下通过 CAS 操作该值，如果 CAS 操作失败，会初始化一个 `cells` 数组，每个线程都会通过取模的方式定位 `cells` 数组中的一个元素，通过这种方式，就将操作单个 `AtomicLong` 的压力分散到数组中的多个元素上。

通过将压力分散，`LongAdder` 可以提供比 `AtomicLong` 更好的性能。获取元素 value 值时，只要将 `base` 与 `cells` 数组中的元素累加即可。

下面是它的原理实现。

```java
	public void increment() {
	        add(1L);
	}

    public void add(long x) {
        Cell[] as;
        long b, v;
        // m = as.length -1，取模用，定位数组角标
        int m;
        // as 数组 m 角标对应的 Cell 对象
        Cell a;
        // 低竞争条件下，cells 为 null，此时调用 casBase（底层为 CAS 操作，类似 AtomicLong） 方法操作 base
        // PS：cells 数组为懒加载，只有在 CAS 竞争失败的情况下才会初始化
        if ((as = cells) != null || !casBase(b = base, b + x)) {
            boolean uncontended = true;
            // as 数组为 null，或者数组 size = 0，或者数组角标（当前线程对 m 取模）在数组中不能定位，或者 cell 对象 CAS 操作失败
            if (as == null || (m = as.length - 1) < 0 || (a = as[getProbe() & m]) == null || !(uncontended = a.cas(v = a.value, v + x)))
                longAccumulate(x, null, uncontended);
        }
    }
```

`LongAdder` 继承自 `Striped64`，底层调用 `Striped64.longAccumulate` 方法实现。

 当第一次调用 `add` 方法时，并不会初始化 `cells` 数组，而是通过 CAS 去操作 `base` 值，操作成功后就直接返回了。

 如果 CAS 操作失败，这时回调用 `longAccumulate` 方法，该方法会初始化 `Cell` 类型的数组，后面所有的操作都会通过该数组实现。PS：可能会有多个线程去初始化 `cells` 数组，但最终只能有一个线程成功，失败的线程还会通过 CAS 去尝试更新 `base` 值。

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

![](https://raw.githubusercontent.com/zhchenme/jdk1.8-source-code-read/master/img/LongAdder2.png)

图片来源 [LongAdder and LongAccumulator in Java](https://www.baeldung.com/java-longadder-and-longaccumulator)

CPU 有多级缓存，这些缓存的最小单位是缓存行（Cache Line），通常情况下一个缓存行的大小是 64 字节(并不绝对，或者是 64 的倍数)。假设现在要操作一个 long 类型的数组，long 在 Java 中占 64 bit，8 个字节，当操作数组中的一个元素时，会从主存中将该元素的相邻的其他元素一起加载进缓存行，即使其他元素你不想操作。

假设两个用 `volatile` 修饰的元素被加载进同一个缓存行，线程 A 更新变量 A 后会将更新后的值刷新回主存，此时缓存行失效（Cache Miss），线程 B 再去操作 B 变量只能重新从主存中读取。这就造成了伪共享(False sharing)问题。

`Cell` 本身没什么好讲的，仔细看一下，这个类被 `@sun.misc.Contended` 注解修饰，这个注解一般在写业务时用不到，但是它可以解决上面的伪共享问题。

`@sun.misc.Contended` 注解在 jdk1.8 中提供，保证缓存行每次缓存一个变量，剩余的空间用字节来填充.

![](https://raw.githubusercontent.com/zhchenme/jdk1.8-source-code-read/master/img/LongAdder2.png)

图片来源 [LongAdder and LongAccumulator in Java](https://www.baeldung.com/java-longadder-and-longaccumulator)

```java

```