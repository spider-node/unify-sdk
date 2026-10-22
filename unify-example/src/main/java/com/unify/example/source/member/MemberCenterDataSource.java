package com.unify.example.source.member;

import com.unify.example.source.SourceIds;
import com.unify.sdk.spi.DataSource;
import com.unify.sdk.spi.SourceContext;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 会员中心数据源的内存实现，模拟数据提供方。
 */
public class MemberCenterDataSource implements DataSource<MemberQueryRequest, MemberInfo> {

    private final Map<String, MemberInfo> members = Map.of(
            "M2001", new MemberInfo("M2001", "王五", "ACTIVE", new MemberInfo.Asset(new BigDecimal("88.5")),
                    1700000000000L, 2),
            "M2002", new MemberInfo("M2002", "赵六", "CANCELLED", new MemberInfo.Asset(BigDecimal.ZERO),
                    1600000000000L, null));

    @Override
    public String sourceId() {
        return SourceIds.MEMBER_CENTER;
    }

    @Override
    public MemberInfo fetch(MemberQueryRequest request, SourceContext context) {
        MemberInfo member = members.get(request.memberId());
        if (member == null || request.includeAsset()) {
            return member;
        }
        return new MemberInfo(member.memberId(), member.nickName(), member.state(), null,
                member.createdAtEpochMs(), member.level());
    }
}
