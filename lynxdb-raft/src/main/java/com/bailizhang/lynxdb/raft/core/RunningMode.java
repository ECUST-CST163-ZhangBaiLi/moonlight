package com.bailizhang.lynxdb.raft.core;

public interface RunningMode {
    /**
     * 需要得到 leader 确认才能启动选举计时器
     */
    String FOLLOWER = "follower";
    /**
     * 需要连接一半以上的节点后才能转换成 candidate
     */
    String CANDIDATE = "candidate";
    /**
     * 选举计时器超时即可转换成 candidate
     */
    String LEADER = "leader";
}
