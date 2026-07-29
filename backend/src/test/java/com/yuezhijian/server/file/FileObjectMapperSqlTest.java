package com.yuezhijian.server.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class FileObjectMapperSqlTest {
    @Test
    void objectAndAttachmentInsertReturnGeneratedIdsWithoutPublicUrls() throws Exception {
        Method objectMethod = FileObjectMapper.class.getMethod("insertFileObject", FileObjectDraft.class);
        Method attachmentMethod = FileObjectMapper.class.getMethod(
                "insertAttachment", long.class, AttachmentDraft.class);
        String objectSql = String.join(" ", objectMethod.getAnnotation(Select.class).value());
        String attachmentSql = String.join(" ", attachmentMethod.getAnnotation(Select.class).value());

        assertThat(objectSql).contains(
                "OUTPUT INSERTED.id", "object_key", "sha256", "'STORE'", "'ACTIVE'")
                .doesNotContain("http://", "https://");
        assertThat(attachmentSql).contains(
                "OUTPUT INSERTED.id", "business_type", "business_id", "store_id");
    }

    @Test
    void deletionRequiresTheBusinessBindingAndSoftDeletesBothRows() throws Exception {
        Method attachmentMethod = FileObjectMapper.class.getMethod(
                "softDeleteAttachment", String.class, long.class, long.class, long.class);
        Method fileMethod = FileObjectMapper.class.getMethod("softDeleteFile", long.class, long.class);
        String attachmentSql = String.join(" ", attachmentMethod.getAnnotation(Update.class).value());
        String fileSql = String.join(" ", fileMethod.getAnnotation(Update.class).value());

        assertThat(attachmentSql).contains(
                "business_type = #{businessType}", "business_id = #{businessId}",
                "removed_at = sysdatetime()", "removed_at IS NULL");
        assertThat(fileSql).contains("status = 'DELETED'", "status = 'ACTIVE'")
                .doesNotContain("DELETE FROM");
    }
}
