package com.yuezhijian.server.member;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
                   m.special_flag AS specialFlag, m.status, m.last_visit_at AS lastVisitAt,
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
                FROM dbo.mem_membership_card
                WHERE member_id = m.id AND status = 'ACTIVE'
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
}
