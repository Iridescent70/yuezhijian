package com.yuezhijian.server.member;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MemberMapper {
    String MEMBER_SELECT = """
            SELECT m.id, m.member_no AS memberNo, active_card.card_no AS membershipCardNo,
                   m.full_name AS fullName, m.nickname, m.mobile_last4 AS mobileLast4,
                   m.gender, m.birthday, m.email, m.source_type AS sourceType,
                   m.join_store_id AS joinStoreId, join_store.store_name AS joinStoreName,
                   m.owner_store_id AS ownerStoreId, owner_store.store_name AS ownerStoreName,
                   m.advisor_employee_id AS advisorEmployeeId,
                   COALESCE(member_level.level_name, N'普通会员') AS levelName,
                   m.special_flag AS specialFlag, m.status,
                   m.frozen_at AS frozenAt, m.freeze_reason AS freezeReason,
                   m.last_visit_at AS lastVisitAt,
                   m.created_at AS createdAt,
                   COALESCE(balance.available_balance, 0) AS availableBalance,
                   COALESCE(balance.frozen_balance, 0) AS frozenBalance,
                   COALESCE(balance.total_recharged, 0) AS totalRecharged,
                   COALESCE(points.available_points, 0) AS availablePoints,
                   COALESCE(points.lifetime_points, 0) AS lifetimePoints,
                   COALESCE(card_stats.card_count, 0) AS cardCount,
                   m.row_version AS rowVersion
            FROM dbo.mem_member m
            JOIN dbo.org_store join_store ON join_store.id = m.join_store_id
            JOIN dbo.org_store owner_store ON owner_store.id = m.owner_store_id
            LEFT JOIN dbo.mem_level member_level ON member_level.id = m.level_id
            LEFT JOIN dbo.ast_balance_account balance ON balance.member_id = m.id
            LEFT JOIN dbo.ast_point_account points ON points.member_id = m.id
            OUTER APPLY (
                SELECT TOP 1 card_no
                FROM dbo.mem_membership_card
                WHERE member_id = m.id AND status = 'ACTIVE'
                ORDER BY registered_at DESC, id DESC
            ) active_card
            OUTER APPLY (
                SELECT COUNT(1) AS card_count
                FROM dbo.ast_member_card
                WHERE member_id = m.id AND status = 'ACTIVE' AND expires_at >= sysdatetime()
            ) card_stats
            """;

    @Select("""
            <script>
            """ + MEMBER_SELECT + """
            WHERE 1 = 1
            <if test="query.keyword != null">
              AND (
                m.member_no LIKE CONCAT('%', #{query.keyword}, '%')
                OR m.full_name LIKE CONCAT('%', #{query.keyword}, '%')
                OR m.mobile_last4 = #{query.keyword}
                <if test="query.mobileHash != null">
                  OR m.mobile_hash = #{query.mobileHash}
                </if>
                OR EXISTS (
                    SELECT 1 FROM dbo.mem_membership_card search_card
                    WHERE search_card.member_id = m.id
                      AND search_card.card_no LIKE CONCAT('%', #{query.keyword}, '%')
                )
              )
            </if>
            <if test="query.storeId != null">
              AND m.owner_store_id = #{query.storeId}
            </if>
            <if test="query.status != null">
              AND m.status = #{query.status}
            </if>
            ORDER BY m.id DESC
            OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY
            </script>
            """)
    List<MemberRow> findPage(
            @Param("query") MemberQuery query,
            @Param("offset") int offset,
            @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM dbo.mem_member m
            WHERE 1 = 1
            <if test="query.keyword != null">
              AND (
                m.member_no LIKE CONCAT('%', #{query.keyword}, '%')
                OR m.full_name LIKE CONCAT('%', #{query.keyword}, '%')
                OR m.mobile_last4 = #{query.keyword}
                <if test="query.mobileHash != null">
                  OR m.mobile_hash = #{query.mobileHash}
                </if>
                OR EXISTS (
                    SELECT 1 FROM dbo.mem_membership_card search_card
                    WHERE search_card.member_id = m.id
                      AND search_card.card_no LIKE CONCAT('%', #{query.keyword}, '%')
                )
              )
            </if>
            <if test="query.storeId != null">
              AND m.owner_store_id = #{query.storeId}
            </if>
            <if test="query.status != null">
              AND m.status = #{query.status}
            </if>
            </script>
            """)
    long count(@Param("query") MemberQuery query);

    @Select(MEMBER_SELECT + " WHERE m.id = #{id}")
    MemberRow findById(long id);

    @Select("""
            SELECT tag.id, tag.tag_code AS code, tag.tag_name AS name,
                   tag.color, tag.negative_flag AS negative
            FROM dbo.mem_member_tag member_tag
            JOIN dbo.mem_tag tag ON tag.id = member_tag.tag_id
            WHERE member_tag.member_id = #{memberId}
              AND member_tag.removed_at IS NULL
              AND tag.status = 'ACTIVE'
            ORDER BY member_tag.assigned_at, tag.id
            """)
    List<MemberTag> findTags(long memberId);

    @Select("SELECT COUNT(1) FROM dbo.mem_member WHERE mobile_hash = #{mobileHash}")
    int countByMobileHash(String mobileHash);

    @Select("SELECT COUNT(1) FROM dbo.mem_member WHERE mobile_hash = #{mobileHash} AND id <> #{memberId}")
    int countByMobileHashExcluding(@Param("mobileHash") String mobileHash, @Param("memberId") long memberId);

    @Select("SELECT TOP 1 id FROM dbo.mem_level WHERE level_code = 'STANDARD' AND status = 'ACTIVE'")
    Long findDefaultLevelId();

    @Select(value = """
            INSERT INTO dbo.mem_member (
                member_no, full_name, nickname, gender, birthday,
                mobile_ciphertext, mobile_hash, mobile_last4, email, source_type,
                join_store_id, owner_store_id, advisor_employee_id, level_id,
                created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{memberNo}, #{fullName}, #{nickname}, #{gender}, #{birthday},
                #{mobileCiphertext}, #{mobileHash}, #{mobileLast4}, #{email}, #{sourceType},
                #{joinStoreId}, #{ownerStoreId}, #{advisorEmployeeId}, #{levelId},
                #{createdBy}, #{createdBy}
            )
            """, affectData = true)
    long insertMember(NewMemberRow member);

    @Insert("""
            INSERT INTO dbo.mem_membership_card (
                member_id, card_no, register_store_id, register_user_id
            ) VALUES (#{memberId}, #{cardNo}, #{storeId}, #{userId})
            """)
    void insertMembershipCard(
            @Param("memberId") long memberId,
            @Param("cardNo") String cardNo,
            @Param("storeId") long storeId,
            @Param("userId") long userId);

    @Insert("INSERT INTO dbo.ast_balance_account (member_id) VALUES (#{memberId})")
    void insertBalanceAccount(long memberId);

    @Insert("INSERT INTO dbo.ast_point_account (member_id) VALUES (#{memberId})")
    void insertPointAccount(long memberId);

    @Insert("""
            INSERT INTO dbo.mem_member_tag (member_id, tag_id, source, assigned_by)
            SELECT #{memberId}, id, 'RULE', #{userId}
            FROM dbo.mem_tag
            WHERE tag_code = 'NEW_MEMBER' AND status = 'ACTIVE'
            """)
    int insertNewMemberTag(@Param("memberId") long memberId, @Param("userId") long userId);

    @Update("""
            <script>
            UPDATE dbo.mem_member
            SET full_name = #{command.fullName}, nickname = #{command.nickname},
                gender = #{command.gender}, birthday = #{command.birthday}, email = #{command.email},
                advisor_employee_id = #{command.advisorEmployeeId}, special_flag = #{command.special},
                <if test="mobileCiphertext != null">
                mobile_ciphertext = #{mobileCiphertext}, mobile_hash = #{mobileHash}, mobile_last4 = #{mobileLast4},
                </if>
                updated_at = sysdatetime(), updated_by = #{command.operatorId}
            WHERE id = #{command.id} AND row_version = #{rowVersion}
            </script>
            """)
    int updateMember(
            @Param("command") MemberUpdateCommand command,
            @Param("mobileCiphertext") String mobileCiphertext,
            @Param("mobileHash") String mobileHash,
            @Param("mobileLast4") String mobileLast4,
            @Param("rowVersion") byte[] rowVersion);

    @Update("""
            UPDATE dbo.mem_member
            SET status = #{command.toStatus},
                frozen_at = CASE WHEN #{command.toStatus} = 'FROZEN' THEN sysdatetime() ELSE NULL END,
                freeze_reason = CASE WHEN #{command.toStatus} = 'FROZEN' THEN #{command.reason} ELSE NULL END,
                updated_at = sysdatetime(), updated_by = #{command.operatorId}
            WHERE id = #{command.id} AND status = #{command.fromStatus} AND row_version = #{rowVersion}
            """)
    int changeStatus(@Param("command") MemberStatusCommand command, @Param("rowVersion") byte[] rowVersion);

    @Insert("""
            INSERT INTO dbo.mem_member_status_log (
                member_id, from_status, to_status, reason, changed_at, changed_by
            ) VALUES (
                #{command.id}, #{command.fromStatus}, #{command.toStatus}, #{command.reason},
                sysdatetime(), #{command.operatorId}
            )
            """)
    void insertStatusLog(@Param("command") MemberStatusCommand command);

    @Select("""
            SELECT id, tag_code AS code, tag_name AS name, tag_source AS source,
                   color, negative_flag AS negative
            FROM dbo.mem_tag
            WHERE status = 'ACTIVE'
            ORDER BY negative_flag, tag_name, id
            """)
    List<MemberTagOption> findTagOptions();

    @Update("""
            UPDATE dbo.mem_member
            SET updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{memberId} AND row_version = #{rowVersion}
            """)
    int touchMember(
            @Param("memberId") long memberId,
            @Param("rowVersion") byte[] rowVersion,
            @Param("operatorId") long operatorId);

    @Update("""
            <script>
            UPDATE dbo.mem_member_tag
            SET removed_at = sysdatetime(), removed_by = #{operatorId}
            WHERE member_id = #{memberId} AND removed_at IS NULL
              AND tag_id IN
              <foreach item="id" collection="tagIds" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int removeTags(
            @Param("memberId") long memberId,
            @Param("tagIds") List<Long> tagIds,
            @Param("operatorId") long operatorId);

    @Insert("""
            <script>
            INSERT INTO dbo.mem_member_tag (member_id, tag_id, source, assigned_by)
            SELECT #{memberId}, tag.id, 'MANUAL', #{operatorId}
            FROM dbo.mem_tag tag
            WHERE tag.status = 'ACTIVE'
              AND tag.id IN
              <foreach item="id" collection="tagIds" open="(" separator="," close=")">#{id}</foreach>
              AND NOT EXISTS (
                  SELECT 1 FROM dbo.mem_member_tag current_tag
                  WHERE current_tag.member_id = #{memberId}
                    AND current_tag.tag_id = tag.id AND current_tag.removed_at IS NULL
              )
            </script>
            """)
    int addTags(
            @Param("memberId") long memberId,
            @Param("tagIds") List<Long> tagIds,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.mem_member
            SET owner_store_id = #{newStoreId}, advisor_employee_id = NULL,
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{memberId} AND owner_store_id = #{oldStoreId}
            """)
    int applyOwnership(
            @Param("memberId") long memberId,
            @Param("oldStoreId") long oldStoreId,
            @Param("newStoreId") long newStoreId,
            @Param("operatorId") long operatorId);
}
