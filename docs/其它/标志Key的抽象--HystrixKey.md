# Key的抽象--HystrixKey
作为Hystrix中一种标识Key，包括：HystrixCommandGroupKey、HystrixCommandKey、HystrixThreadPoolKey
1. 这个接口定义的方法并不是 `getKey()`，而是 `name()`，这样可以很好地兼容枚举类（枚举类恰好维护了此Key类型下所有的Key）
2. 每个Key类型都有一个默认的不带抽象方法的抽象类实现，比如HystrixKey的默认实现HystrixKeyDefault
3. 每个Key类型的实现都由InternMap来管理，其Map的key即为 `name()`
## HystrixCommandKey
1. Hystrix采用命令者模式来对服务进行调用，每个命令使用HystrixCommandKey进行标识，默认为类名：`getClass().getSimpleName();`
2. 每个Command一般代表着一个功能服务
## HystrixCommandGroupKey
Hystrix对Command的分组，同时也是默认的 `HystrixThreadPoolKey`
## HystrixThreadPoolKey
代表一个 `HystrixThreadPool` 线程池，即表示着服务调用的隔离，一个线程池可能包含多个Command；这个Key在监控、统计、缓存等时候会用到。

