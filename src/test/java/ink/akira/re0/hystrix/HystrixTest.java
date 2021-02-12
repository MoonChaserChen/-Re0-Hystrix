package ink.akira.re0.hystrix;

import com.netflix.hystrix.*;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.Assert.*;

/**
 * 测试CommandHelloWorld
 *
 * @author 雪行
 * @date 2021/2/10 5:26 下午
 */
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
