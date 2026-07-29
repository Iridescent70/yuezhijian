package com.yuezhijian.server.job;

public interface AsyncJobHandler {
    String jobType();

    AsyncJobExecutionResult execute(AsyncJobTask task);
}
