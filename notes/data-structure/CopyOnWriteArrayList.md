### 一、CopyOnWriteArrayList 概述

**1.1 概念概述**

`CopyOnWriteArrayList` 是 juc 包下一个线程安全的并发容器，底层使用数组实现。CopyOnWrite 顾名思义是写时复制的意思，其基本思路是，从一开始大家都在共享同一个内容，当某个人想要修改这个内容的时候，会把内容 Copy 出去形成一个新的内容然后再进行修改，这是一种延时懒惰策略。

假设往一个容器添加元素的时候，不直接往当前容器添加，而是加锁后先将当前容器进行 Copy，复制出一个新的容器，然后在新的容器里添加元素，添加完元素之后，再将原容器的引用指向新的容器，最后释放锁。这样做的好处是我们可以对 CopyOnWrite 容器进行并发的读，而不需要加锁，只有在修改时才加锁，从而达到读写分离的效果。

**1.2 特性**

 - 每次对数组中的元素进行修改时都会创建一个新数组，因此没有扩容机制
 - 读元素不加锁，修改元素时才加锁
 - 允许存储 `null` 元素
 - `CopyOnWriteArraySet` 底层原理使用的是 `CopyOnWriteArrayList`
 - `CopyOnWriteArrayList` 使用与读多写少的场景，如果写场景比较多的场景下比较消耗内存

**1.3 图解**

下面是一个 `CopyOnWriteArrayList` 的原理图：
![在这里插入图片描述](https://img-blog.csdnimg.cn/2019021519452898.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NvZGVqYXM=,size_16,color_FFFFFF,t_70)


### 二、源码分析

**2.1 内部属性与相关方法**

``` java
    /**
     * 显示锁对象
     */
    final transient ReentrantLock lock = new ReentrantLock();
    /**
     * 内部底层数组，volatile 关键字修饰
     */
    private transient volatile Object[] array;
    /**
     * 返回内部 array
     */
    final Object[] getArray() {
        return array;
    }
    /**
     * 设置 array
     */
    final void setArray(Object[] a) {
        array = a;
    }
```

`CopyOnWriteArrayList` 写锁使用的是 `ReentrantLock`，底层是一个被 `volatile` 关键字修饰的数组。构造函数相对来说也比较简单，就不介绍了，有兴趣的可以自己查看下。

**2.2 add 方法**

``` java
    public boolean add(E e) {
        // 加锁
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            // 把原数组中的元素拷贝到新数组中，并使数组容量 + 1
            Object[] newElements = Arrays.copyOf(elements, len + 1);
            // 新元素添加到新数组中
            newElements[len] = e;
            // 重新赋值 array
            setArray(newElements);
            return true;
        } finally {
            // 释放锁
            lock.unlock();
        }
    }
```

源码很容易理解，添加元素的时候，创建一个新数组，长度为原数组长度加 1，将原数组中的元素拷贝到新数组中，在新数组中插入元素即可。

**2.3 get 方法**

``` java
    public E get(int index) {
        return get(getArray(), index);
    }
    
    
    private E get(Object[] a, int index) {
        return (E) a[index];
    }
```

注意：获取元素的方法是不需要加锁的。

**2.4 remove 方法**

``` java
    public E remove(int index) {
        final ReentrantLock lock = this.lock;
        // 加锁
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            // 获取指定位置上的元素值
            E oldValue = get(elements, index);
            int numMoved = len - index - 1;
            // 如果移除的是数组中最后一个元素，复制元素时直接舍弃最后一个
            if (numMoved == 0)
                setArray(Arrays.copyOf(elements, len - 1));
            else {
                // 初始化新数组为原数组长度减 1
                Object[] newElements = new Object[len - 1];
                // 分两次完成元素拷贝
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index + 1, newElements, index,
                                 numMoved);
                // 重置 array
                setArray(newElements);
            }
            // 返回老 value
            return oldValue;
        } finally {
            // 释放锁
            lock.unlock();
        }
    }
```

删除指定位置上的元素时会创建一个原数组长度减 1 的新数组，然后以 `index` 索引为标记，分两次拷贝，将元素拷贝到新数组中。

**2.5 addIfAbsent 方法**

总的来说，`CopyOnWriteArrayList` 内部的方法都比较容易理解，相对比较难理解的有两个方法：

 - `remove(Object o)`：移除指定的元素值（只会移除第一次出现的）
 - `addIfAbsent(E e)`：元素不存在的条件下才添加
 
这两个方法都涉及到了快照（`snapshot`）对象，下面是具体代码：

``` java
    public boolean addIfAbsent(E e) {
        // 因为这里不加锁，因此使用快照对象
        Object[] snapshot = getArray();
        // 从头开始查找，如果该元素已存在，则返回 false，不存在才添加
        return indexOf(e, snapshot, 0, snapshot.length) >= 0 ? false :
            addIfAbsent(e, snapshot);
    }
```

`indexof` 方法用于返回指定元素在原数组中的位置，不存在返回 -1，下面是源代码：

``` java
    private static int indexOf(Object o, Object[] elements,
                               int index, int fence) {
        // null 与非 null 元素走不同的查找逻辑
        if (o == null) {
            for (int i = index; i < fence; i++)
                if (elements[i] == null)
                    return i;
        } else {
            for (int i = index; i < fence; i++)
                if (o.equals(elements[i]))
                    return i;
        }
        // 没有该元素返回 -1
        return -1;
    }
```
接下来就是一个比较高能的方法了：
    
``` java
    private boolean addIfAbsent(E e, Object[] snapshot) {
        // 加锁
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // 获取当前数组
            Object[] current = getArray();
            int len = current.length;
            /**
             * 并发环境下造成的不一致情况
             * 因为获取快照数组的时候没有加锁，别的线程可能修改了原数组，
             * 就会造成快照数组与原数组不一致
             */
            if (snapshot != current) {
                // Optimize for lost race to another addXXX operation
                // 获取较小的数组长度
                int common = Math.min(snapshot.length, len);
                // 可以分两种情况考虑，1 添加的时候删除了元素 2 一直添加元素
                for (int i = 0; i < common; i++)
                    // 如果添加的元素在原数组中存在，则结束循环
                    if (current[i] != snapshot[i] && eq(e, current[i]))
                        return false;
                    // 和上面的 for 循环正好组成一个数组大小，用于判断原数组中是否已经存在添加的元素
                if (indexOf(e, current, common, len) >= 0)
                        return false;
            }
            // 要添加的元素在原数组中不存在
            Object[] newElements = Arrays.copyOf(current, len + 1);
            newElements[len] = e;
            setArray(newElements);
            return true;
        } finally {
            // 释放锁
            lock.unlock();
        }
    }
```

在并发情况下，因为获取的快照数组与原数组可能不一致（假如别的线程已经新增了元素），因此需要重新判断添加的元素是否已经存在。

⚠️：大家可能会问直接加锁不就解决问题了吗，为什么非要搞一个快照文件呢？搞得这么麻烦，因为使用快照数组在访问非常频繁的情况下可以提升一点性能。另一个并发容器 `CopyOnWriteArrayList` 底层就是用 `CopyOnWriteArrayList` 存储元素，它的添加方法调用的就是 `addIfAbsent` 方法，这样就不难理解为什么要这么做了。

另外一个方法 `remove(Object o)` 也用到了快照数组，比 `addIfAbsent` 稍微难理解一点，有兴趣的可以自己查看。

### 参考

[http://ifeve.com/java-copy-on-write/](http://ifeve.com/java-copy-on-write/)