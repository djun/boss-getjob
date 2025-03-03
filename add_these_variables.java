// 添加这些变量到BossResumeService类的顶部，与其他类变量一起
private long lastBackOperationTime = 0;
private static final long MIN_BACK_INTERVAL = 8000; // 两次返回操作之间的最小间隔时间（8秒） 