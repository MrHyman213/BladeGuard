package com.knifecerts;

// Feature: blade-guardian-full-implementation, Property 11: Инвариант архивирования при замене фото

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knifecerts.model.Knife;
import com.knifecerts.repository.BrandRepository;
import com.knifecerts.repository.KnifeModelRepository;
import com.knifecerts.repository.KnifeRepository;
import com.knifecerts.repository.SubmissionBufferRepository;
import com.knifecerts.service.MainMenuUpdateService;
import com.knifecerts.service.SubmissionBufferService;
import com.knifecerts.service.YandexDiskService;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based тесты для YandexDiskService / SubmissionBufferService.
 * Feature: blade-guardian-full-implementation
 */
class YandexDiskServicePropertyTest {

    /**
     * Property 11: Инвариант архивирования при замене фото
     *
     * После замены фото:
     * - старый путь должен быть перемещён в app:/archive/replaced/
     * - новый путь должен быть установлен в записи ножа (app:/certificates/...)
     *
     * **Validates: Requirements 19.1, 19.2**
     */
    @Property(tries = 100)
    void photoReplacementArchivesOldAndSetsNew(
            @ForAll("oldPhotoPaths") String oldPath,
            @ForAll("newPhotoPaths") String newPath) throws IOException {

        // Arrange
        YandexDiskService yandexDiskService = mock(YandexDiskService.class);
        KnifeRepository knifeRepository = mock(KnifeRepository.class);
        BrandRepository brandRepository = mock(BrandRepository.class);
        KnifeModelRepository knifeModelRepository = mock(KnifeModelRepository.class);
        SubmissionBufferRepository submissionBufferRepository = mock(SubmissionBufferRepository.class);
        MainMenuUpdateService mainMenuUpdateService = mock(MainMenuUpdateService.class);

        SubmissionBufferService service = new SubmissionBufferService(
                submissionBufferRepository,
                brandRepository,
                knifeModelRepository,
                knifeRepository,
                yandexDiskService,
                mainMenuUpdateService);

        Knife knife = mock(Knife.class);
        when(knife.getPhotoPath()).thenReturn(oldPath);
        when(knifeRepository.findById(1L)).thenReturn(Optional.of(knife));
        when(knifeRepository.save(knife)).thenReturn(knife);

        // Act
        service.replaceKnifePhoto(1L, newPath);

        // Assert: старый путь перемещён в app:/archive/replaced/
        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        verify(yandexDiskService).moveFile(eq(oldPath), destCaptor.capture());
        assertThat(destCaptor.getValue()).startsWith("app:/archive/replaced/");

        // Assert: новый путь установлен в записи ножа
        verify(knife).setPhotoPath(newPath);
        assertThat(newPath).startsWith("app:/certificates/");
    }

    @Provide
    Arbitrary<String> oldPhotoPaths() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(4)
                .ofMaxLength(20)
                .map(s -> "app:/certificates/photo_" + s + ".jpg");
    }

    @Provide
    Arbitrary<String> newPhotoPaths() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(4)
                .ofMaxLength(20)
                .map(s -> "app:/certificates/photo_" + s + ".jpg");
    }
}
