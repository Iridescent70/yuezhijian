package com.yuezhijian.server.file;

public record StoredFileDownload(StoredFileObject file, byte[] content) {
    public StoredFileDownload {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
