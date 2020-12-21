- [构造函数](#构造函数)
- [线程池运行状态](#线程池运行状态)
- [线程池类型](#线程池类型)
  * [newFixedThreadPool](#newfixedthreadpool)
  * [newSingleThreadExecutor](#newsinglethreadexecutor)
  * [newCachedThreadPool](#newcachedthreadpool)
  * [ScheduledExecutorService](#scheduledexecutorservice)
- [源码核心方法](#源码核心方法)
  * [`execute`方法](#execute方法)
  * [`addWorker`方法](#addworker方法)
  * [addWorkerFailed方法](#addWorkerFailed方法)
  * [runWorker方法](#runWorker方法)
  * [processWorkerExit方法](#processWorkerExit方法)
  * [getTask()方法](#getTask()方法)
  * [shutdown方法](#shutdown方法)
  * [shutdownNow方法](#shutdownNow方法)
  * [tryTerminate方法](#tryTerminate方法)
  * [submit方法执行流程](#submit方法执行流程)
- [线程池工作线程创建流程](#线程池工作线程创建流程)
- [总结](#总结)
  * [submit与execute方法的区别](#submit与execute方法的区别)
  * [submit提交任务异常处理](#submit提交任务异常处理)
  * [什么是工作线程退出](#什么是工作线程退出)
  * [工作线程退出的场景](#工作线程退出的场景)
  * [核心线程与非核心线程的区别](#核心线程与非核心线程的区别)
  * [Worker为什么实现AQS](#Worker为什么实现AQS)
  * [线程池内的线程如何响应中断](#线程池内的线程如何响应中断)
  * [为什么缓存线程池内的线程可以回收](#为什么缓存线程池内的线程可以回收)
  * [线程池设置多少线程数合理](#线程池设置多少线程数合理)
### 构造函数

```java
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }
```

核心参数：

 - corePoolSize：线程池核心线程数最大值
 - maximumPoolSize：线程池最大线程数
 - keepAliveTime：线程存活时间
 - unit：线程存活时间单位
 - workQueue：存放任务的阻塞队列
   - ArrayBlockingQueue
   - LinkedBlockingQueue
   - PriorityBlockingQueue
   - SynchronousQueue
   - DelayQueue
 - threadFactory：线程池创建线程的工厂
 - handler：线程池饱和处理策略
   - AbortPolicy：丢弃任务并抛出异常，默认的拒绝策略
   - CallerRunsPolicy：继续执行
   - DiscardPolicy：丢弃任务
   - DiscardOldestPolicy：丢弃旧的任务，执行新的任务

### 线程池运行状态

```java
    // 低 29 位表示线程池中线程数，高 3 位表示线程池的运行状态，默认是运行状态
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    // COUNT_BITS = 29
    private static final int COUNT_BITS = Integer.SIZE - 3;
    // 容量 00011111 11111111 11111111 11111111
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // RUNNING = 11100000 00000000 00000000 00000000
    private static final int RUNNING    = -1 << COUNT_BITS;
    // SHUTDOWN = 00000000 00000000 00000000 00000000
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    // STOP = 00100000 00000000 00000000 00000000
    private static final int STOP       =  1 << COUNT_BITS;
    // TIDYING = 01000000 00000000 00000000 00000000
    private static final int TIDYING    =  2 << COUNT_BITS;
    // TERMINATED = 01100000 00000000 00000000 00000000
    private static final int TERMINATED =  3 << COUNT_BITS;
```

 - RUNNING：运行状态，也是线程池初始化默认状态
 - SHUTDOWN：线程池不接收新的任务，但会处理队列中已添加的任务
 - STOP：不接收新任务且不处理队列中已添加的任务
 - TIDYING：线程池销毁前的中间状态
 - TERMINATED：线程池销毁

### 线程池类型

#### newFixedThreadPool

线程数量固定的线程池

```java
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads,
                                      0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue<Runnable>());
    }
```

#### newSingleThreadExecutor

只有一个线程的线程池

```java
    public static ExecutorService newSingleThreadExecutor() {
        return new FinalizableDelegatedExecutorService
            (new ThreadPoolExecutor(1, 1,
                                    0L, TimeUnit.MILLISECONDS,
                                    new LinkedBlockingQueue<Runnable>()));
    }
```

#### newCachedThreadPool

可缓存的线程池，极端情况下会创建过多的线程，线程来不及回收耗尽 CPU 和内存资源。由于空闲 60 秒线程会被终止，长时间保持空闲的 `CachedThreadPool` 不会占用任何资源。

缓存线程池的核心线程数为 0，因此创建的都是非核心线程，非核心线程最重要的一点是可以回收，因此当队列中没有任务时，线程可以完全回收。

```java
    public static ExecutorService newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>());
    }
```

#### ScheduledExecutorService

可延迟执行任务或定期执行的线程池。

```java
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return new ScheduledThreadPoolExecutor(corePoolSize);
    }
```

### 源码核心方法

#### `execute`方法

```java
    public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();
        int c = ctl.get();
        // 如果执行的线程数小于核心线程数，则创建线程
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true))
                return;
            c = ctl.get();
        }
        // 执行线程数大于线程池核心线程池，任务入队
        if (isRunning(c) && workQueue.offer(command)) {
            // 用于检查线程池状态
            int recheck = ctl.get();
            // 如果线程池处于非运行状态，且尝试从队列中删除该线程成功，则执行拒绝策略
            if (! isRunning(recheck) && remove(command))
                reject(command);
                // 如果线程池中工作线程为 0，则创建非核心线程，保证入队的任务一定执行
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        // 创建线程失败且加入队列失败后执行拒绝策略
        else if (!addWorker(command, false))
            reject(command);
    }
```

 - 当线程池内工作线程小于核心线程数，直接创建核心线程执行当前任务
 - 反之将任务加入到阻塞队列，任务入队后判断线程池状态
    - 如果线程池处于非运行状态，从队列中移除任务，移除成功执行拒绝策略
    - 线程池处于运行状态，但是工作线程为 0，则创建一个非核心线程，保证队列中的任务执行
 - 如果加入阻塞队列失败，尝试创建非核心线程，非核心线程创建失败会执行拒绝策略

#### `addWorker`方法

```java
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        /**
         * 两个循环，一个循环线程池状态一个循环 workers 数量
         * 获取正在执行的线程数，如果线程数大于最大值，或 core 为 true 的情况下，执行的线程数大于核心线程数，则返回 false
         */
        for (;;) {
            // 获取控制状态值
            int c = ctl.get();
            // 获取当前线程状态
            int rs = runStateOf(c);

            // (rs == SHUTDOWN && firstTask == null && ! workQueue.isEmpty()) 表示线程池处于 SHUTDOWN 状态，此时不接收新的任务，但是会执行队列中的任务
            if (rs >= SHUTDOWN && ! (rs == SHUTDOWN && firstTask == null && ! workQueue.isEmpty()))
                return false;

            for (;;) {
                // 获得正在执行的线程数
                int wc = workerCountOf(c);
                // 如果正在执行的线程数大于最大值，或者大于核心线程数或最大线程数则返回 false
                if (wc >= CAPACITY || wc >= (core ? corePoolSize : maximumPoolSize))
                    return false;
                // 添加线程数成功后结束循环
                if (compareAndIncrementWorkerCount(c))
                    break retry;
                c = ctl.get();  // Re-read ctl
                if (runStateOf(c) != rs)
                    continue retry;
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            w = new Worker(firstTask);
            // 获取线程对象
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                // 加锁，加锁的目的是为了校验线程池状态，并维护 largestPoolSize 值
                mainLock.lock();
                try {
                    // 获取线程池的状态
                    int rs = runStateOf(ctl.get());

                    if (rs < SHUTDOWN ||
                            // shutdown 状态不能提交新的任务，但可以创建新工作线程
                            (rs == SHUTDOWN && firstTask == null)) {
                        // 检查线程状态
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        // 把工作线程添加到队列中
                        workers.add(w);
                        int s = workers.size();
                        // 记录线程池历史最大的工作线程个数
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        // 添加成功
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                // 如果添加成功，则开启线程
                if (workerAdded) {
                    // 启动 worker 对应的线程
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            // 如果线程没有开启，表示创建失败，需要从集合中移除
            if (! workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }
```

 - 线程池状态校验，校验状态通过后将线程池内的工作线程数 + 1
    - `(rs == SHUTDOWN && firstTask == null && ! workQueue.isEmpty())` 表示线程池状态为 `SHUTDOWN`，创建的是非核心线程，并且队列中仍有任务未执行。我们知道当线程池处于这种状态时线程池是会继续执行队列中任务的
 - 线程池内工作线程校验，工作线程数量不能大于 `CAPACITY`，如果创建的是核心线程不能核心线程数，如果是非核心线程不能大于线程池最大容量
 - 创建 `Worker` 对象，再次校验线程池状态，校验通过后将创建的 `Worker` 加入到工作线程集合中
 - 开启 `Worker` 内的工作线程
 - 工作线程可能开启失败，失败执行 `addWorkerFailed` 方法

前面说了条件校验通过会将工作线程数 + 1。后面如果出现异常了，是不是应该将运行的线程数 -1，并将 `workers` 集合中的 `Worker` 删除保持幂等，下面看一下 `addWorkerFailed` 方法。

#### addWorkerFailed方法

```java
   private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)
                workers.remove(w);
            // 工作线程数 -1
            decrementWorkerCount();
            // 尝试销毁线程池
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }
```

确实和上面猜想的一样，另外还一个步骤尝试销毁线程池，这个方法会在下面介绍。

#### runWorker方法

任务通过 `Worker` 对象进行封装，在初始化 `Worker` 时还创建了执行该任务的线程，最后调用 `Worker` 中的线程的 `start` 方法执行任务。`Worker` 本身继承自 `Runnable` 接口，因此当执行 `start`  也就是执行 `Worker` 的 `run` 方法。

```java
    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        // 解除锁状态，允许响应中断
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
            // 当前 worker 绑定的任务执行完成后自旋从缓存的阻塞队列中获取任务
            while (task != null || (task = getTask()) != null) {
                /**
                 * 这里为什么要用 AQS 实现加锁，原因是在 shutdown 线程池时
                 * 会 tryLock 中断闲置的线程，
                 * tryLock成功说明该线程没有任务执行，由此判断线程是否处于空闲状态
                 * 但是队列中的任务执行不能受影响
                 * 此时执行队列任务的线程已经加锁，是不会被中断的
                 */
                w.lock();
                if ((runStateAtLeast(ctl.get(), STOP) || (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP))) && !wt.isInterrupted())
                    wt.interrupt();
                try {
                    // 执行任务前可以做一些额外处理
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        // 这里调用的是任务的 run 而不是 start 方法，因此线程池中的任务不具备线程特性，具备线程特性的是线程池中的线程
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        // 可以实现该方法，用来处理异常
                        afterExecute(task, thrown);
                    }
                } finally {
                    // 这里置 null 下一次循环的时候会调用 getTask() 从队列中获取等待的任务
                    task = null;
                    // 执行的任务数 ++
                    w.completedTasks++;
                    w.unlock();
                }
            }
            // 只有出现异常时 processWorkerExit 为 true，出现异常后 worker 数量 -1
            completedAbruptly = false;
        } finally {
            // 能走到这里说明队列中已经没有要执行的任务了，但是并不绝对，worker 退出回收
            processWorkerExit(w, completedAbruptly);
        }
    }
```

如果 `Worker` 中的任务没有执行，则用 `Worker` 的线程执行 `Worker` 的任务，如果 `Worker` 的任务执行完了，则通过循环的方式调用 `getTask` 从阻塞队列中获取任务，如果获取到则执行队列中的任务。

任务执行的过程中是加锁的，这里为什么要用 AQS 加锁呢？后面结合 `shutdown` 方法再来详细分析这个问题。

如果 while 循环正常退出，意味着线程要回收，当异常出现也会退出 while 循环，此时 `completedAbruptly` 标记为 true，线程退出会做什么呢？下面来看一下 `processWorkerExit` 方法。

#### processWorkerExit方法

```java
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        /**
         * 正常情况下，线程在 getTask() 方法中会回收
         * 另一种情况也会被回收：任务执行过程中出现异常，completedAbruptly 为 true 表示出现异常
         */
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 统计所有线程执行的任务数
            completedTaskCount += w.completedTasks;
            // 移除当前 worker
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        // 尝试销毁线程池
        tryTerminate();

        int c = ctl.get();
        // 线程状态小于 STOP 如果队列中有任务，这些任务应该被执行
        if (runStateLessThan(c, STOP)) {
            // !completedAbruptly 没有出现异常
            if (!completedAbruptly) {
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                // 如果核心线程允许回收且任务队列中还有任务，那么工作线程数量最少为 1，保证剩下的任务被执行
                if (min == 0 && ! workQueue.isEmpty())
                    min = 1;
                // 判断是否需要创建非核心线程处理队列中的任务
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            // 队列中的任务补偿，防止所有线程回收，队列中的任务没有线程执行，添加一个空任务的线程，处理队列中的任务
            addWorker(null, false);
        }
    }
```

 - `completedAbruptly` 为 true，表示任务执行过程中出现异常，该线程退出，需要减少线程池内运行的线程数
 - 统计该线程执行的任务数，从 `worders` 集合中移除当前 worker
 - 尝试销毁线程池
 - 线程能正常退出意味着队列中已经没有任务了，当然退出流程没走完的情况下可能又有任务被添加到了队列中，退出之前需要判断线程池内是否还有线程，如果没有线程，就需要创建非核心线程，保证队列中的任务被执行

`allowCoreThreadTimeOut` 字段为 true 表示线程池内的核心线程可以被回收，这个字段只能在 `allowCoreThreadTimeOut(boolean value)` 方法赋值，默认为 false，所以我们创建的线程池，核心线程默认是不会被回收的。

`allowCoreThreadTimeOut` 为 false，线程正常退出，如果线程池内的线程数小于核心线程数，还是会创建非核心线程。

`allowCoreThreadTimeOut` 为 true，且队列不为空，线程池内的线程数必须大于等于 1，如果没有线程，则创建一个非核心线程，保证队列中的任务会被执行。

#### getTask()方法

```java
    private Runnable getTask() {
        boolean timedOut = false; // Did the last poll() time out?

        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);

            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }

            int wc = workerCountOf(c);

            /**
             * allowCoreThreadTimeOut = true，表示核心线程数可以回收
             * allowCoreThreadTimeOut 为 false，表示核心线程不能被回收，但是 wc > corePoolSize（存在非核心线程），timed 也会被标记为 true，此时会将非核心线程回收
             * 当工作线程在 keepAliveTime 时间内，没有获取到可执行的任务，那么该工作线程就要被回收
             */
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            /**
             * (wc > maximumPoolSize || (timed && timedOut)) 表示线程池内线程容量已经达到最大值或者，或者核心线程数可以被回收且在超时时间内没有获取到任务
             * (wc > 1 || workQueue.isEmpty()) 表示存在工作线程，或者队列为空，此时将回收线程池内的线程
             */
            if ((wc > maximumPoolSize || (timed && timedOut)) && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            try {
                // 从任务队列中获取任务，如果线程可以回收，在超时时间内队列中没有获取到任务，则将该线程回收
                Runnable r = timed ? workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) : workQueue.take();
                if (r != null)
                    return r;
                // poll 超时没有获取到任务
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }
```

 - 如果线程池状态为 `STOP`、`TIDYING` 或者 `TERMINATED` 意味着线程池将要被销毁，或者线程池状态为 `SHUTDOWN` 且队列中任务为空，线程回收
 - `timed` 是线程超时回收标记，如果线程池内的核心线程允许回收，或者线程池内存在非核心线程，`timed = true`
 - 根据条件判断是否需要回收线程，根据 `timed` 我们可以得出核心线程与非核心线程都是可以回收的
    - `(wc > maximumPoolSize || (timed && timedOut))` 表示线程池内线程容量已经达到最大值或者核心线程数可以被回收且在超时时间内没有获取到任务
    - `(wc > 1 || workQueue.isEmpty())` 表示存在工作线程，或者队列为空
 - `timed = true` 如果在 `keepAliveTime` 时间内没有获取到任务，将 `timedOut` 标记为 false，结束本轮循环，下轮循环就可以将该线程回收

#### shutdown方法

```java
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 检查权限
            checkShutdownAccess();
            // 更新线程池状态为 SHUTDOWN
            advanceRunState(SHUTDOWN);
            // 中断所有空闲的 workers
            interruptIdleWorkers();
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
    }
```

 - 检查 shutdown 权限
 - 更新线程池状态为 `SHUTDOWN`
 - 中断线程池中所有闲置的工作线程

```java
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                // worker 处于非中断状态，且尝试获取锁成功，进行中断请求，尝试获取锁成功，意味着该 Worker 没有在执行任务
                // @see java.util.concurrent.ThreadPoolExecutor.runWorker
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }
```

`interruptIdleWorkers` 方法名很明确：中断线程池中空闲的线程，线程池是怎么定义空闲线程的呢？

在中断线程之前通过 `w.tryLock()` 尝试获取锁，这时候 `Worker` 实现 AQS 的作用就体现出来了，在 `runWorker` 方法中，当 `Worker` 执行任务时会 lock，任务执行完成后释放锁，这个锁也是依赖 `Worker` 通过 AQS 实现的。线程池中在执行任务的工作线程是不会被中断的，除了执行任务的线程，其他的线程都是空闲线程，是可以中断的。

#### shutdownNow方法

```java
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(STOP);
            // 遍历中断所有的 workers
            interruptWorkers();
            // 清空并返回队列中的任务
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        // 将未执行的任务返回出去，用户自定义处理
        return tasks;
    }

    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                // 将任务从队列中移除，因此 shutdownNow 方法会清空队列中的任务
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }
```

`shutdown` 与 `shutdownNow` 方法最终要的区别是 `shutdownNow` 会中断所有的工作线程，而不局限于闲置的工作线程，接着会销毁线程池。如果队列中有未执行完的任务会将这些任务清空并返回出去供业务方自行处理。

```java
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 中断所有已经开启的线程
            for (Worker w : workers)
                w.interruptIfStarted();
        } finally {
            mainLock.unlock();
        }
    }
```

与 `interruptIdleWorkers` 方法不同的是 `interruptWorkers` 不会尝试获取锁，因此不管线程池内的线程有没有在执行任务都会被中断。

#### tryTerminate方法

```java
    final void tryTerminate() {
        for (;;) {
            int c = ctl.get();
            /**
             * isRunning(c)：处于 RUNNING 状态的线程池不可销毁
             * runStateAtLeast(c, TIDYING) 表示线程池已经被销毁
             * (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty())：当线程池 SHUTDOWN 时，但是队列中仍然有任务，则会继续执行完剩下的任务
             */
            if (isRunning(c) ||
                    runStateAtLeast(c, TIDYING) ||
                    (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))
                return;
            // 如果线程池将要被销毁且工作的线程数不为 0，则中断发送中断信号，此时线程池不能销毁
            if (workerCountOf(c) != 0) { // Eligible to terminate
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            // 终止线程池
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // 将线程状态重置为 TIDYING，重置成功后，线程池被销毁，并重置状态为 TERMINATED
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        terminated();
                    } finally {
                        ctl.set(ctlOf(TERMINATED, 0));
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }
```

 - 销毁线程池之前线进行状态校验
    - 运行中的线程池不能销毁
    - 已经销毁的线程池不能销毁
    - 线程池状态为 `SHUTDOWN`，但是队列中仍然还有任务，线程池不能销毁
 - 如果存在工作线程，则发送一个中断，该中断只会中断所有空闲线程中的一个，`tryTerminate()` 在很多场景中都有调用
 - 销毁线程池

#### submit方法执行流程

```java
    public Future<?> submit(Runnable task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<Void> ftask = newTaskFor(task, null);
        execute(ftask);
        return ftask;
    }

    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new FutureTask<T>(runnable, value);
    }

    public FutureTask(Runnable runnable, V result) {
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;       // ensure visibility of callable
    }

    public static <T> Callable<T> callable(Runnable task, T result) {
        if (task == null)
            throw new NullPointerException();
        return new RunnableAdapter<T>(task, result);
    }
```

任务通过 `FutureTask` 进行包装，`FutureTask` 本身继承了 `Runnable`，当任务执行时会先执行 `FutureTask` 的 `run` 方法。

如果 `submit` 方法的入参是 `Runnable` 类型的任务，会通过适配器转化成 `Callable` 类型。

`FutureTask` 的 `run` 方法实现如下：

```java
    public void run() {
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return;
        try {
            Callable<V> c = callable;
            if (c != null && state == NEW) {
                V result;
                boolean ran;
                try {
                    result = c.call();
                    ran = true;
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    setException(ex);
                }
                if (ran)
                    set(result);
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            int s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
    }
```

当任务执行时，如果出现异常并不会抛出，而是通过 `setException` 方法，这也意味着如果你的任务没有自己手动捕获异常时，当任务出现异常，线程池不会有任何提醒，如果你想得到提醒，可以调用 `FutureTask.get()` 方法。

### 线程池工作线程创建流程

```java
        Worker(Runnable firstTask) {
            setState(-1); // inhibit interrupts until runWorker
            this.firstTask = firstTask;
            // 使用工厂创建一个线程
            this.thread = getThreadFactory().newThread(this);
        }
```

`Worker` 初始化时通过 `getThreadFactory().newThread(this)` 创建工作线程。如果不在 `ThreadPoolExecutor` 对象初始化时指定线程工厂对象，会执行 `Executors.defaultThreadFactory()` 默认的方法获取：

```java
    public static ThreadFactory defaultThreadFactory() {
        return new DefaultThreadFactory();
    }

    static class DefaultThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        DefaultThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                                  Thread.currentThread().getThreadGroup();
            namePrefix = "pool-" +
                          poolNumber.getAndIncrement() +
                         "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                                  namePrefix + threadNumber.getAndIncrement(),
                                  0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
```

### 总结

#### submit与execute方法的区别

 - `execute` 执行任务时会抛出异常
 - `submit` 方法会返回一个 `RunnableFuture` 对象，通过这个对象能够得知当前任务执行的状态等信息，切当出现异常时不会抛出异常

#### submit提交任务异常处理

上面分析了 `submit` 方法在执行任务时，如果出现异常，会将异常传递到 `setException(ex)` 方法，这个方法实现是不处理异常的。如果要捕获并处理异常，可以通过以下几种方式：

 1. `try catch`，在业务中手动捕获异常
 2. 通过调用 `FutureTask.get()` 获取异常结果
 3. 为 `ThreadPoolExecutor` 自定义 `ThreadFactory`，并为线程池中的工作线程自定义 `UncaughtExceptionHandler`
 4. 重写 `ThreadPoolExecutor` 的 `afterExecute`方法，这个方法默认是个空实现

```java
    @Test
    public void test() {
        ExecutorService threadPool = Executors.newFixedThreadPool(1, r -> {
            Thread t = new Thread(r);
            t.setUncaughtExceptionHandler((t1, e) -> System.out.println(t1.getName() + "线程抛出的异常" + e));
            return t;
        });
        threadPool.execute(() -> {
            Object object = null;
            System.out.print("result## " + object.toString());
        });
    }
```

#### 什么是工作线程退出

`start` 方法开启线程，当任务执行完会自动退出。

#### 工作线程退出的场景

退出可以分为两种情况，一种正常退出，一种是非正常(异常)退出。

正常退出：

 - `allowCoreThreadTimeOut` 被标记为 true，核心线程在 `keepAliveTime` 时间范围内没有从队列中获取到任务，核心线程回收，`allowCoreThreadTimeOut` 默认为 false
 - 线程池内的线程数大于核心线程数，意味着存在非核心线程，如果有线程在 `keepAliveTime` 时间范围内没有从队列中获取到任务，非核心线程回收

非正常退出：

 - 创建 `Worker`时出现异常，`Worker` 线程没有跑起来，这时会回收线程

#### 核心线程与非核心线程的区别

从本质上来说核心线程与非核心线程没有任何区别，我们通常说核心线程回收、非核心线程回收，其实这种说法并不准确，只是为了更好帮助我们理解线程池才这么描述。在某种前提下，线程回收的是规定时间内没有从队列中获取到任务的线程，并不区分什么核心线程与非核心线程。

#### Worker为什么实现AQS

调用线程池的 `shutdown` 的方法时会中断所有空闲的线程，线程池是通过什么定义空闲的呢？明白这个问题就很好理解了。

`shutdown` 方法在中断线程之前会调用 `Worker` 的 `tryLock` 方法，如果成功会中断线程。加锁成功就说明这个 `Worker` 的工作线程是空闲的。加锁失败说明这个工作线程在忙，此时还不能中断它，那这个线程在忙什么呢？答案在 `runWorker` 方法中，当工作线程获取到任务，执行任务之前会 `w.lock()`，任务执行完会释放锁。

所以就有了一个结论：在执行任务的线程不能中断，那些尝试从队列中获取任务的线程可以中断。

#### 线程池内的线程如何响应中断

在看这个问题之前我们需要知道什么是线程中断：

线程中断只是一个信号，并不具备阻塞、退出功能，客户端可以选择判断线程的中断标记，具体做什么由客户端自己处理。如果中断的客户端处于阻塞状态（wait、sleep、join），则抛出 `InterruptedException` 异常

`shutdown` 与 `shutdownNow` 方法都会给线程池内的线程发送中断信号，当工作线程接收到这个中断信号会做哪些事呢？

可中断的线程有两部分：空闲线程，执行任务的线程。

空闲线程被中断：在 `getTask` 方法中自旋，尝试从阻塞队列中获取任务，如果线程池的 `keepAliveTime  > 0`，从队列中获取不到任务时会阻塞一段时间，如果此时被中断，会抛出中断异常，但是线程池并不会抛出一个异常，而是将 `timedOut` 设置为 false。

```java
        try {
                Runnable r = timed ? workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) : workQueue.take();
                if (r != null)
                    return r;
                // poll 超时没有获取到任务
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
```

工作线程被中断：工作线程被中断，会抛出异常，如果执行的是 `submit()` 方法提交的任务，这个异常会被传递到 `afterExecute` 方法中。异常在 `runWorker` 方法抛出后，还会记录到 `afterExecute` 方法中，这个方法是个扩展实现，可以通过实现该方法自定义异常处理逻辑。

```java
                try {
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        afterExecute(task, thrown);
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
```

PS：工作线程被中断不表示要回收。

#### 为什么缓存线程池内的线程可以回收

```java
    public static ExecutorService newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>());
    }
```

这个问题要从构造函数中找，缓存线程池的核心线程数设置为 0，意味着创建的都是非核心线程，所有都是可以回收的。

纸上得来终觉浅，绝知此事要躬行。

#### 线程池设置多少线程数合理

在看这个问题之前，需要先了解多线程的目的是什么？多线程的目的：加快 IO 密集型任务。

假设一个线程执行任务过程中，本地计算时间为 x，等待时间为 y，那么一个 N 核的 CPU，当线程数为 `N*(x+y)/x` 时 CPU 可以跑到 100%。

参考：

[面试必备：Java线程池解析](https://juejin.im/post/6844903889678893063)
[线程数究竟设多少合理](https://www.cnblogs.com/jajian/p/10862365.html)
