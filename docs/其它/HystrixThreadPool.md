# HystrixThreadPool
用于执行 `HystrixCommand#run()` 的线程池。当隔离策略配置 `ExecutionIsolationStrategy` 为线程 `THREAD` 时才会用到。
## HystrixThreadPool与HystrixThreadPoolKey
HystrixThreadPoolKey即为这个线程池的标志
## HystrixThreadPool与HystrixCommand
1. 每一个HystrixCommand表示着一个功能服务，通过对这个HystrixCommand指定HystrixThreadPoolKey进行做到调用隔离。
在不指定HystrixThreadPoolKey时，默认会使用HystrixCommandGroupKey创建一个对应的HystrixThreadPoolKey
> com.netflix.hystrix.HystrixCommand.Setter.andThreadPoolKey(HystrixThreadPoolKey threadPoolKey)
2. 每次创建HystrixCommand时都会指定线程池相关属性，但是只有第一次创建线程池时才会进行属性设置。
3. 线程池的标志 + 线程池的属性
> static HystrixThreadPool getInstance(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties.Setter propertiesBuilder)
## 线程池
本质上还是用的JDK中的ExecutorService线程池，多个线程池用 `ConcurrentHashMap<String, HystrixThreadPool> threadPools` 进行维护，
这里Map的Key即为 `HystrixThreadPoolKey.name()`

## HystrixThreadPoolProperties
HystrixThreadPool相关属性
| 字段 | 说明 | 默认值 |
| ---- | ---- | ---- |
| corePoolSize | 核心线程数 | default_coreSize = 10 |
| maximumPoolSize | 最大线程数(但不一定是传给线程池的最大线程数) | default_maximumSize = 10 |
| keepAliveTime | 非核心线程数存活时间，单位：分钟 | default_keepAliveTimeMinutes = 1 |
| maxQueueSize | 队列大小 | default_maxQueueSize = -1，使用SynchronousQueue |
| queueSizeRejectionThreshold | 队列允许大小，由于不能在运行中改变线程池队列大小，因此可以通过此参数进行控制 | default_queueSizeRejectionThreshold = 5 |
| allowMaximumSizeToDivergeFromCoreSize | 影响实际最大线程数 | default_allow_maximum_size_to_diverge_from_core_size = false |
| threadPoolRollingNumberStatisticalWindowInMilliseconds | 线程池滑动统计窗口 | default_threadPoolRollingNumberStatisticalWindow = 10000 |
| threadPoolRollingNumberStatisticalWindowBuckets | 线程池滑动统计窗口bucket数 | default_threadPoolRollingNumberStatisticalWindowBuckets = 10 |

### 实际的最大线程数
HystrixThreadPoolProperties中的maximumPoolSize并不一定是传给线程池的最大线程数，而是通过以下方法获取的才是。（有点不能理解，coreSize > maximumSize应当不被允许才对）
```
public Integer actualMaximumSize() {
    final int coreSize = coreSize().get();
    final int maximumSize = maximumSize().get();
    if (getAllowMaximumSizeToDivergeFromCoreSize().get()) {
        if (coreSize > maximumSize) {
            return coreSize;
        } else {
            return maximumSize;
        }
    } else {
        return coreSize;
    }
}
```
换句话说的是： actualMaximumSize = allowMaximumSizeToDivergeFromCoreSize ? coreSize : max(coreSize, maximumSize)，
参见 `HystrixConcurrencyStrategy.getThreadPool(HystrixThreadPoolKey, HystrixThreadPoolProperties)'`：
```
public ThreadPoolExecutor getThreadPool(final HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties threadPoolProperties) {
    // 其它代码

    if (allowMaximumSizeToDivergeFromCoreSize) {
        final int dynamicMaximumSize = threadPoolProperties.maximumSize().get();
        if (dynamicCoreSize > dynamicMaximumSize) {
            logger.error("Hystrix ThreadPool configuration at startup for : " + threadPoolKey.name() + " is trying to set coreSize = " +
                    dynamicCoreSize + " and maximumSize = " + dynamicMaximumSize + ".  Maximum size will be set to " +
                    dynamicCoreSize + ", the coreSize value, since it must be equal to or greater than the coreSize value");
            return new ThreadPoolExecutor(dynamicCoreSize, dynamicCoreSize, keepAliveTime, TimeUnit.MINUTES, workQueue, threadFactory);
        } else {
            return new ThreadPoolExecutor(dynamicCoreSize, dynamicMaximumSize, keepAliveTime, TimeUnit.MINUTES, workQueue, threadFactory);
        }
    } else {
        return new ThreadPoolExecutor(dynamicCoreSize, dynamicCoreSize, keepAliveTime, TimeUnit.MINUTES, workQueue, threadFactory);
    }
}
```

## HystrixConcurrencyStrategy
与线程池相关的一些策略，比如：
1. 获取线程池
2. 获取线程工厂
3. 获取阻塞队列
### 线程池
Hystrix使用的线程池为 `ThreadPoolExecutor`, 创建参数为：
```
return new ThreadPoolExecutor(dynamicCoreSize, actualMaximumSize, keepAliveTime, TimeUnit.MINUTES, workQueue, threadFactory);
```
其中：
dynamicCoreSize、keepAliveTime均为HystrixThreadPoolProperties中配置；
actualMaximumSize为 `HystrixThreadPoolProperties.actualMaximumSize()`
threadFactory & workQueue如下
### 使用线程工厂ThreadFactory
这里还可能使用google的`com.google.appengine.api.ThreadManager`提供的ThreadFactory，当然默认情况下还是会使用简单的ThreadFactory，
参见 `HystrixConcurrencyStrategy.getThreadFactory(HystrixThreadPoolKey)`
```
private static ThreadFactory getThreadFactory(final HystrixThreadPoolKey threadPoolKey) {
    if (!PlatformSpecific.isAppEngineStandardEnvironment()) {
        return new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "hystrix-" + threadPoolKey.name() + "-" + threadNumber.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }

        };
    } else {
        return PlatformSpecific.getAppEngineThreadFactory();
    }
}
```
这里简单的ThreadFactory只设置了线程的名称以及设置为daemon threads（当只有daemon threads时，JVM会退出）
### 阻塞队列
Hystrix可能使用的阻塞队列有SynchronousQueue、LinkedBlockingQueue，参见： `HystrixConcurrencyStrategy.getBlockingQueue(int)`
```
public BlockingQueue<Runnable> getBlockingQueue(int maxQueueSize) {
    if (maxQueueSize <= 0) {
        return new SynchronousQueue<Runnable>();
    } else {
        return new LinkedBlockingQueue<Runnable>(maxQueueSize);
    }
}
```
由于默认maxQueueSize = -1，因此默认的BlockingQueue为SynchronousQueue
## 其它
1. HystrixThreadPool定义了Command执行相关的三个状态，看起来是用于metrics做统计的？
```
public void markThreadExecution();
public void markThreadCompletion();
public void markThreadRejection();
```
2. HystrixThreadPoolDefault中ThreadPoolExecutor已经具有任务队列了&相关属性，为什么这里还定义一个queue&properties&queueSize？
```java
static class HystrixThreadPoolDefault implements HystrixThreadPool {
    private final HystrixThreadPoolProperties properties;
    private final BlockingQueue<Runnable> queue;
    private final ThreadPoolExecutor threadPool;
    private final HystrixThreadPoolMetrics metrics;
    private final int queueSize;

    // 其它代码 
}
```