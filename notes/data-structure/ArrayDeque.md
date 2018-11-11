### 一、ArrayDeque 介绍

`Deque` 的含义是“double ended queue”，即双端队列，它既可以当作栈使用，也可以当作队列使用。

从名字可以看出 `ArrayDeque` 底层通过数组实现，为了满足可以同时在数组两端插入或删除元素的需求，该数组还必须是循环的，即循环数组（circular array），也就是说数组的任何一点都可能被看作起点或者终点。`ArrayDeque` 是非线程安全的（not thread-safe）；另外，该容器不允许放入 `null` 元素。

![](https://raw.githubusercontent.com/CarpenterLee/JCFInternals/master/PNGFigures/ArrayDeque_base.png)

上图中我们看到，**`head` 指向首端第一个有效元素，`tail` 指向尾端第一个可以插入元素的空位。**因为是循环数组，所以 `head` 不一定总等于 0，`tail` 也不一定总是比 `head `大。


### 二、ArrayDeque 相关方法

#### `addFirst()`


`addFirst(E e)` 的作用是在 `Deque` 的首端插入元素，也就是在 `head` 的前面插入元素，在空间足够且下标没有越界的情况下，只需要将 `elements[--head] = e` 即可。

![](https://raw.githubusercontent.com/CarpenterLee/JCFInternals/master/PNGFigures/ArrayDeque_addFirst.png)

实际需要考虑：1.空间是否够用，以及2.下标是否越界的问题。上图中，如果 `head` 为 0 之后接着调用`addFirst()`，虽然空余空间还够用，但 `head` 为`-1`，下标越界了。下面代码通过相与的运算很好的解决了这个问题：

``` java
    public void addFirst(E e) {
        // 非空校验
        if (e == null)
            throw new NullPointerException();
        /**
         *  (head - 1) & (elements.length - 1) 相当于取余
         *
         *  如果 head = 0，相当于 -1 & (elements.length - 1)
         *  -1 的二进表示：0000 0001 -> 1111 1110 + 1 -> 1111 1111
         *  & 出来的结果就是 elements.length - 1
         */
        elements[head = (head - 1) & (elements.length - 1)] = e;
        // 当头尾相遇时对数组进行扩容
        if (head == tail)
            doubleCapacity();
    }
```

#### `doubleCapacity()`

逻辑是申请一个更大的数组（原数组的两倍），然后将原数组复制过去。过程如下图所示：

![](https://github.com/CarpenterLee/JCFInternals/raw/master/PNGFigures/ArrayDeque_doubleCapacity.png)

图中我们看到，复制分两次进行，第一次复制 `head` 右边的元素，第二次复制 `head` 左边的元素。

源代码如下：

``` java
    private void doubleCapacity() {
        // 当尾节点指向头节点的时候才继续执行
        assert head == tail;
        int p = head;
        int n = elements.length;
        // 头结点右边元素数量
        int r = n - p; // number of elements to the right of p
        // 将数组容量扩大 2 倍
        int newCapacity = n << 1;
        if (newCapacity < 0)
            throw new IllegalStateException("Sorry, deque too big");
        Object[] a = new Object[newCapacity];
        // 将原数组中 head 右边的数据拷贝到新数组中
        System.arraycopy(elements, p, a, 0, r);
        //将原数组中 head 左边的元素拷贝到新数组中
        System.arraycopy(elements, 0, a, r, p);
        elements = a;
        // 重置头尾节点
        head = 0;
        tail = n;
    }
```

#### `addLast()`

`addLast(E e)` 的作用是在 `Deque` 的尾端插入元素，也就是在 `tail` 的位置插入元素，由于 `tail` 总是指向下一个可以插入的空位，因此只需要 `elements[tail] = e`即可。插入完成后再检查空间，如果空间已经用光，则调用 `doubleCapacity()` 进行扩容。

``` java 
    public void addLast(E e) {
        // 插入的元素不允许为  null
        if (e == null)
            throw new NullPointerException();
        // 在尾节点插入元素
        elements[tail] = e;
        // 尾节点位置 + 1，并判断是否需要扩容
        if ( (tail = (tail + 1) & (elements.length - 1)) == head)
            doubleCapacity();
    }
```

### 引用

[https://github.com/CarpenterLee/JCFInternals](https://github.com/CarpenterLee/JCFInternals)