# Hystrix的三方依赖
Hystrix包括以下三方依赖
| 依赖 | 作用 | 备注 |
| ---- | ---- | ---- |
| archaius-core | 配置管理库 | netflix自己的，应该算二方包 |
| HdrHistogram | 延时百分比统计 |
| rxjava | 响应式编程、异步执行模式 | 使用的是1.X版本，现流行的是2.X版本 |
| slf4j-api | 日志facade | |