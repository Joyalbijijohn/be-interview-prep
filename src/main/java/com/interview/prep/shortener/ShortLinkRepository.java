package com.interview.prep.shortener;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByCode(String code);

    @Modifying
    @Query("""
            update ShortLink l set l.visitCount = l.visitCount + 1
            where l.code = :code and (l.expiresAt is null or l.expiresAt > :now)""")
    int incrementVisits(@Param("code") String code, @Param("now") Instant now);
}
