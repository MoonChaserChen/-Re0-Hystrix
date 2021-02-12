package ink.akira.re0.hystrix;

import com.netflix.hystrix.HystrixCommand;

/**
 * @author 雪行
 * @date 2021/2/12 5:01 下午
 */
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