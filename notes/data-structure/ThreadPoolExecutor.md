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
 - keepAliveTime：线程池中非核心线程空闲的存活时间，默认情况下，只有当线程池中的线程数大于 `corePoolSize` 时
 - unit：线程空闲存活时间单位
 - workQueue：存放任务的阻塞队列
   - ArrayBlockingQueue
   - LinkedBlockingQueue
   - PriorityBlockingQueue
   - SynchronousQueue
   - DelayQueue
 - threadFactory：线程池创建线程的工厂
 - handler：线程池饱和策略处理策略
   - AbortPolicy：丢弃任务并抛出异常，默认的拒绝策略
   - CallerRunsPolicy：继续执行
   - DiscardPolicy：丢弃任务
   - DiscardOldestPolicy：丢弃旧的任务，执行新的任务

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

可缓存的线程池，极端情况下会创建过多的线程，耗尽 CPU 和内存资源。由于空闲 60 秒的线程会被终止，长时间保持空闲的 `CachedThreadPool` 不会占用任何资源。

```java
    public static ExecutorService newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>());
    }
```

#### ScheduledExecutorService

可延迟执行任务或定期执行的线程池

```java
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return new ScheduledThreadPoolExecutor(corePoolSize);
    }
```

### 源码核心方法

#### `execute` 方法

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
                // 如果线程池中工作线程为 0（线程池已关闭），则添加一个空的任务
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        // 创建线程失败且加入队列失败后执行拒绝策略
        else if (!addWorker(command, false))
            reject(command);
    }
```

当线程池内工作线程小于核心线程数，直接创建线程执行当前任务，反之将任务加入到阻塞队列。

#### `addWorker` 方法

```java
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        // 外层循环，检查线程池运行状态
        for (;;) {
            // 获取控制状态值
            int c = ctl.get();
            // 获取当前线程状态
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.
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
                // else CAS failed due to workerCount change; retry inner loop
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            w = new Worker(firstTask);
            // 获取线程对象（执行任务的线程）
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    // 获取线程池的状态
                    int rs = runStateOf(ctl.get());

                    if (rs < SHUTDOWN ||
                            // shutdown 状态不能提交新的任务，但可以创建新工作线程
                            (rs == SHUTDOWN && firstTask == null)) {
                        // 检查线程状态，如果已经执行抛出异常
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        // 把工作线程添加到队列中
                        workers.add(w);
                        int s = workers.size();
                        // 如果工作线程数大于最大线程数，则不处理后续的线程
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
                    // 当执行 start 方法启动线程 thread 时，本质是执行了 Worker 的 run 方法
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            // 如果线程没有开启，需要从集合中移除
            if (! workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }
```

任务通过 `Worker` 对象进行封装，在初始化 `Worker` 时还创建了执行该任务的线程，最后调用 `Worker` 中的线程的 `start` 方法执行任务。`Worker` 本身继承自 `Runnable` 接口，因此当执行 `start`  也就是执行 `Worker` 的 `run` 方法。

### runWorker 方法

```java
    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        // 获取任务
        Runnable task = w.firstTask;
        w.firstTask = null;
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
            // 从 Worker 中或阻塞队列中获取任务
            while (task != null || (task = getTask()) != null) {
                w.lock();
                // 判断是否需要中断线程，当阻塞队列中没有任务时会被中断
                if ((runStateAtLeast(ctl.get(), STOP) || (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP))) && !wt.isInterrupted())
                    wt.interrupt();
                try {
                    // 执行任务前可以做一些额外处理，空实现
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        // 执行线程任务
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
                    // 这里置 null 用处是在下一次循环的时候会调用 getTask() 从队列中获取等待的任务
                    task = null;
                    // 执行的任务数 ++
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
            // worker 退出
            processWorkerExit(w, completedAbruptly);
        }
    }
```

如果 `Worker` 中的任务没有执行，则用 `Worker` 中的线程执行 `Worker` 中的任务，如果 `Worker` 的任务执行完了，则通过循环的方式从阻塞队列中获取任务，如果获取到则执行队列中的任务。

PS：这里有一个疑问，自己没有搞明白，这里标记一下：
队列中的任务执行是通过 `getTask()` 方法获取阻塞队列中的任务循环执行的，那么有没有可能 `task == null`，此时队列中没有添加任务，循环终止，而后续有任务被添加进队列中，那后续的任务要怎么触发执行呢？

### submit 方法执行流程

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

这里只分析了一些核心流程，其他方法调用有兴趣的自己感兴趣看下。

### 常见面试问题

接下来分析一些常见的面试题目。

#### submit 与 execute 方法的区别

 - `execute` 执行任务时会抛出异常
 - `submit` 方法会返回一个 `RunnableFuture` 对象，通过这个对象能够得知当前任务执行的状态等信息，切当出现异常时不会抛出异常

#### submit 提交任务异常处理

上面分析了 `submit` 方法在执行任务时，如果出现异常，会将异常传递到 `setException(ex)` 方法，这个方法实现是不处理异常的。如果要捕获并处理异常，可以通过以下几种方式：

 1. `try catch`，手动捕获异常
 2. 通过调用 `FutureTask.get()` 获取异常结果
 3. 为 `ThreadPoolExecutor` 自定义 `ThreadFactory`，并为线程池中的工作线程设置 `UncaughtExceptionHandler`
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
      
参考：

[面试必备：Java线程池解析](https://juejin.im/post/6844903889678893063)
