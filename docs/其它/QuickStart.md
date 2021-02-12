# QuickStart
## HelloWorld
Hystrix主要是通过 `HystrixCommand` 使用命令者模式，将原有逻辑包裹在 `run()` 方法里。
- 模拟一个远程调用Service
```java
public class RemoteService {
    private final long wait;

    public RemoteService(long wait) {
        this.wait = wait;
    }

    String execute() throws InterruptedException {
        Thread.sleep(wait);
        return "Success";
    }
}
```
- 构建其对应的HystrixCommand
```java
public class RemoteServiceCommand extends HystrixCommand<String> {
    private final RemoteService remoteService;

    public RemoteServiceCommand(Setter setter, RemoteService remoteService) {
        super(setter);
        this.remoteService = remoteService;
    }

    @Override
    protected String run() throws Exception {
        return remoteService.execute();
    }
}
```
- 正常调用
```java
public class HystrixTest {
    @Test
    public void testNoTimeout() {
        HystrixCommand.Setter setter = HystrixCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("HelloWorld"))
                .andCommandPropertiesDefaults(
                        HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(5_000)
                );

        assertEquals("Success", new RemoteServiceCommand(setter, new RemoteService(3_000)).execute());
    }
}
```
- 调用超时
```java
public class HystrixTest {
    @Test(expected = HystrixRuntimeException.class)
    public void testTimeout() {
        HystrixCommand.Setter setter = HystrixCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("HelloWorld"))
                .andCommandPropertiesDefaults(
                        HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(3_000)
                );

        assertEquals("Success", new RemoteServiceCommand(setter, new RemoteService(5_000)).execute());
    }
}
```
- 降级fallback
在熔断器启动、线程池/信号量已满、非HystrixBadRequestException异常、run()/construct()运行超时时将会执行降级逻辑。只需要重写getFallback方法即可
```java
public class RemoteServiceCommand extends HystrixCommand<String> {
    private final RemoteService remoteService;

    public RemoteServiceCommand(Setter setter, RemoteService remoteService) {
        super(setter);
        this.remoteService = remoteService;
    }

    @Override
    protected String run() throws Exception {
        return remoteService.execute();
    }

    @Override
    protected String getFallback() {
        return "Fail";
    }
}
```
- 限流(Limited Thread Pool)
```java
public class HystrixTest {
    @Test
    public void testThreadLimit() throws InterruptedException, ExecutionException {
        HystrixCommand.Setter setter = HystrixCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("HelloWorld"))
                .andCommandPropertiesDefaults(
                        HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(3_000)
                ).andThreadPoolPropertiesDefaults(
                        HystrixThreadPoolProperties.Setter()
                                .withMaxQueueSize(3)
                                .withCoreSize(1)
                                .withQueueSizeRejectionThreshold(3)
                );

        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            results.add(new RemoteServiceCommand(setter, new RemoteService(500)).queue());
        }

        for (int i = 0; i < results.size(); i++) {
            assertEquals(i <= 3 ? "Success" : "Fail", results.get(i).get());
        }
    }
}
```
配置1个核心线程+容量为3的队列，因此只有前4个任务能成功执行，后面的任务全部会失败。
- 熔断
模拟一个从fail到recover的服务：设置恢复时间，在恢复时间前调用服务直接抛异常
```java
public class RemoteService {
    private final LocalDateTime recoverTime;

    public RemoteService(LocalDateTime recoverTime) {
        this.recoverTime = recoverTime;
    }

    String execute() throws Exception {
        if (recoverTime.isAfter(LocalDateTime.now())) {
            throw new Exception("Service is not Ok");
        }
        Thread.sleep(200);
        return "Success";
    }
}
```
完成其对应HystrixCommand
```java
public class RemoteServiceCommand extends HystrixCommand<String> {
    private final RemoteService remoteService;

    public RemoteServiceCommand(Setter setter, RemoteService remoteService) {
        super(setter);
        this.remoteService = remoteService;
    }

    @Override
    protected String run() throws Exception {
        return remoteService.execute();
    }

    @Override
    protected String getFallback() {
        return "Fail";
    }
}
```
调用测试
```java
public class HystrixTest {
    public static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    @Test
    public void testCircuitBreaker() throws InterruptedException {
        HystrixCommand.Setter setter = HystrixCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("HelloWorld"))
                .andCommandPropertiesDefaults(
                        HystrixCommandProperties.Setter()
                                .withCircuitBreakerRequestVolumeThreshold(1)
                );

        LocalDateTime recoverTime = LocalDateTime.now().plusSeconds(2);
        System.out.println("Recover time: " + dtf.format(recoverTime));
        ExecutorService es = Executors.newFixedThreadPool(1);
        es.submit(() -> {
            while (true) {
                String result = new RemoteServiceCommand(setter, new RemoteService(recoverTime)).execute();
                System.out.println(dtf.format(LocalDateTime.now()) + ": " + result);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignore) {
                }
            }
        });
        es.shutdown();
        es.awaitTermination(10, TimeUnit.SECONDS);
    }
}
```
2秒后服务恢复，但是要大约5秒后服务调用才会成功，中间3秒处于熔断器打开状态，直接fallback。

## SpringBoot-Hystrix
1. 依赖
```dependency
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-hystrix</artifactId>
    <version>2.2.7.RELEASE</version>
</dependency>
```
2. EnableHystrix
```java
@SpringBootApplication
@EnableHystrix
public class SbApplication {
    public static void main(String[] args) {
        SpringApplication.run(SbApplication.class, args);
    }
}
```
3. 使用
```java
@Service
public class SimulateRemoteService {
    @HystrixCommand(fallbackMethod = "defaultHello")
    public String hello(String name) {
        try {
            Thread.sleep(1_200);
        } catch (InterruptedException ignored) {
        }
        return "Hello, " + name + "!";
    }

    public String defaultHello(String name) {
        return "Hello, " + name;
    }
}
```



