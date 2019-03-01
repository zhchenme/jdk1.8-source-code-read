AQS 可以说是 jdk 源码里非常难理解的一个知识了，功力有限，看了源码之后只弄懂了其大致的原理，具体实现细节还需要慢慢的去理解。

看 AQS 之前应该先知道 `volatile` 关键字与 CAS 相关的知识。这里只简单的以共享模式为例分析一下其内部源码，等有了更深层次的理解后再与大家分享。

### 一、Node 队列

AQS 内部是一个基于双向队列来实现独占与共享的同步器，双向队列通过内部 `Node` 类维护，`Node` 节点有 4 种状态，AQS 就是通过这几种状态来维护队列内的线程，状态值为 0 时表示初始化状态。

``` java
        /**
         * 当前线程被取消
         */
        static final int CANCELLED =  1;
        /**
         * 表明当前的后继结点正在或者将要被阻塞（通过使用 LockSupport.pack 方法）
         * 当前的节点被释放（release）或者被取消时（cancel）时，要唤醒它的后继结点
         *（通过 LockSupport.unpark 方法）
         */
        static final int SIGNAL    = -1;
        /**
         * 线程在 condition 队列
         */
        static final int CONDITION = -2;
        /**
         * 传播共享锁
         */
        static final int PROPAGATE = -3;
```

### 二、独占锁原理

这里我把获取同步状态分为以下几个步骤：

- 尝试获取同步状态，获取成功直接返回
- 获取同步状态失败后加入同步队列
- 在同步队列中自旋尝试去获取同步状态，如果获取成功，直接返回
- 如果没有获取到同步状态，就判断自己是否需要休息（waiting）
- 放弃获取同步状态后一些额外处理工作

**2.1 入口**

``` java
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
                /* 获取同步状态失败后通过 addWaiter 方法将当前线程加入同步队列尾 */
                acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            // 获取锁失败并且加入队列失败则线程中断
            selfInterrupt();
    }
```

**2.2 加入同步队列**

获取同步状态后加入同步队列。

``` java
    private Node addWaiter(Node mode) {
        // 创建节点
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        Node pred = tail;
        // 如果尾节点存在，则直接将当前节点插入在尾节点后
        if (pred != null) {
            node.prev = pred;
            // cas
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        // 如果是第一次插入节点，通过 enq 方法初始化头节点
        enq(node);
        return node;
    }
```

``` java
    private Node enq(final Node node) {
        // 自旋
        for (;;) {
            // 获取尾节点
            Node t = tail;
            if (t == null) { // Must initialize
                // 队列中没有节点，初始化头节点
                // TODO 头节点没有数据（线程，锁类型）？
                if (compareAndSetHead(new Node()))
                    tail = head;
            } else {
                node.prev = t;
                // 重置尾节点为当前插入的节点
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }
```

**2.3 加入同步队列后自旋**

``` java
    final boolean acquireQueued(final Node node, int arg) {
        // 记录是否成功获取同步状态
        boolean failed = true;
        try {
            // 记录是否发生线程中断
            boolean interrupted = false;
            // 自旋
            for (;;) {
                // 获取前驱节点
                final Node p = node.predecessor();
                // 如果前驱节点是头节点，第二个节点尝试获取同步状态且获取同步状态成功
                // 获取同步状态成功后把已经执行过的头节点断开
                if (p == head && tryAcquire(arg)) {
                    // 重置头节点为当前节点，会将头节点的 thread 与 pre 置 null
                    setHead(node);
                    // 断开旧的头节点
                    p.next = null; // help GC
                    // 成功获取，将 failed 置为 false
                    failed = false;
                    return interrupted;
                }
                // 判断线程是否需要阻塞（进入waiting状态，直到被 unpark() 唤醒）
                if (shouldParkAfterFailedAcquire(p, node) &&
                        // 阻塞线程并返回是否产生中断
                        parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            // 获取过程中发生异常并且没有获取到同步状态，则取消获取（也可以理解为自旋过程中一直没有获取到同步状态）
            if (failed)
                cancelAcquire(node);
        }
    }
```

**2.4 获取失败，是否等待**

``` java
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        // 获取前置节点的等待状态
        int ws = pred.waitStatus;
        // SIGNAL 表明当前的后继结点正在或者将要被阻塞（前置节点获取成功后会唤醒后继节点）
        if (ws == Node.SIGNAL)
            /*
             * This node has already set status asking a release
             * to signal it, so it can safely park.
             */
            return true;
         // 如果前驱节点已经成功获取过同步状态
        if (ws > 0) {
            /*
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             *
             * 如果前驱已经被执行过？则一直向前查找，直到找到未获取的线程节点
             * 这里是理解为已经成功获取过还是说以前的节点放弃了，理解为前面有的节点放弃了获取会好些
             *
             */
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            // 删除已经获取过的线程节点
            pred.next = node;
        } else {
            /*
             * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             *
             * 通过 cas 将状态置为 "后继节点需要执行"，告诉前置节点你执行过以后需要通知后面的节点执行
             */
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }
```

**2.5 需要等待(wating)**

``` java
    private final boolean parkAndCheckInterrupt() {
        // 调用 park() 使线程进入 waiting 状态
        LockSupport.park(this);
        return Thread.interrupted();
    }
```

**2.6 放弃获取后的操作**

``` java
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null)
            return;

        // 当前节点的线程置 null
        node.thread = null;

        // Skip cancelled predecessors
        // 获取前一个节点
        Node pred = node.prev;
        // 跳过已经被取消的节点
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        // predNext is the apparent node to unsplice. CASes below will
        // fail if not, in which case, we lost race vs another cancel
        // or signal, so no further action is necessary.
        // TODO predNext = node?
        Node predNext = pred.next;

        // Can use unconditional write instead of CAS here.
        // After this atomic step, other Nodes can skip past us.
        // Before, we are free of interference from other threads.
        // 将当前节点的状态设置为已取消
        node.waitStatus = Node.CANCELLED;

        // If we are the tail, remove ourselves.
        // 如果删除的是尾节点，直接把前一个节点置为尾节点
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {
            // If successor needs signal, try to set pred's next-link
            // so it will get one. Otherwise wake it up to propagate.
            int ws;
            // 删除的既不是头节点也不是尾节点
            if (pred != head &&
                    ((ws = pred.waitStatus) == Node.SIGNAL ||
                            (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                    pred.thread != null) {
                // 获取当前节点的 next 节点，用于删除当前节点
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    // 断开当前节点
                    compareAndSetNext(pred, predNext, next);
            } else {
                // 删除的是头节点，通过 unparkSuccessor 唤醒后继线程
                unparkSuccessor(node);
            }

            // 指向自己，GC 回收
            node.next = node; // help GC
        }
    }
```

``` java
    private final boolean parkAndCheckInterrupt() {
        // 调用 park() 使线程进入 waiting 状态
        LockSupport.park(this);
        return Thread.interrupted();
    }
```