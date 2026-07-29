package com.yuezhijian.server.member;

public record MemberQuery(String keyword, Long storeId, String status, int page, int size, String mobileHash) {
    public int offset() {
        return (page - 1) * size;
    }
}
