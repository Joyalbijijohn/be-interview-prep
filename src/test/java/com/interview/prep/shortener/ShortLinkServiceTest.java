package com.interview.prep.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ShortLinkServiceTest {

    private final ShortLinkRepository repository = mock(ShortLinkRepository.class);
    private final CodeGenerator generator = mock(CodeGenerator.class);
    private final ShortLinkService service = new ShortLinkService(repository, generator);

    @Test
    void retriesWithNewCodeWhenCodeCollides() {
        when(generator.next()).thenReturn("dup", "fresh");
        when(repository.saveAndFlush(any(ShortLink.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShortLink link = service.create("https://example.com", null);

        assertThat(link.getCode()).isEqualTo("fresh");
        verify(repository, times(2)).saveAndFlush(any(ShortLink.class));
    }

    @Test
    void givesUpAfterRepeatedCollisions() {
        when(generator.next()).thenReturn("dup");
        when(repository.saveAndFlush(any(ShortLink.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create("https://example.com", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(repository, times(5)).saveAndFlush(any(ShortLink.class));
    }
}
