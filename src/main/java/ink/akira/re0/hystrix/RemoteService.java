package ink.akira.re0.hystrix;

import java.time.LocalDateTime;

/**
 * 模拟远程服务
 *
 * @author 雪行
 * @date 2021/2/12 4:59 下午
 */
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
