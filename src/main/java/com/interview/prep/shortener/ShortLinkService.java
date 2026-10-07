package com.interview.prep.shortener;

import com.interview.prep.common.GoneException;
import com.interview.prep.common.NotFoundException;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortLinkService {

    private static final int MAX_CODE_ATTEMPTS = 5;

    private final ShortLinkRepository repository;
    private final CodeGenerator codeGenerator;

    public ShortLinkService(ShortLinkRepository repository, CodeGenerator codeGenerator) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
    }

    public ShortLink create(String url, Instant expiresAt) {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            try {
                return repository.saveAndFlush(new ShortLink(codeGenerator.next(), url, expiresAt));
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_CODE_ATTEMPTS - 1) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    @Transactional
    public String visit(String code) {
        ShortLink link = find(code);
        if (repository.incrementVisits(code, Instant.now()) == 0) {
            throw new GoneException("Short link " + code + " has expired");
        }
        return link.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(String code) {
        return StatsResponse.from(find(code));
    }

    private ShortLink find(String code) {
        return repository.findByCode(code).orElseThrow(() -> new NotFoundException("Short link " + code + " not found"));
    }
}
