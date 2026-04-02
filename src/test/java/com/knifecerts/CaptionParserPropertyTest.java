package com.knifecerts;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knifecerts.dto.AlternativeEntry;
import com.knifecerts.dto.ParsedCaption;
import com.knifecerts.service.AlternativesParserImpl;
import com.knifecerts.service.CaptionParserImpl;
import com.knifecerts.service.SettingsService;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based тесты для CaptionParser и AlternativesParser.
 */
public class CaptionParserPropertyTest {

    private SettingsService createSettingsService(String separator) {
        SettingsService mock = mock(SettingsService.class);
        when(mock.getAlternativeSeparator()).thenReturn(separator);
        return mock;
    }

    @Property(tries = 100)
    void captionRoundTrip(@ForAll("captions") String caption) {
        SettingsService settingsService = createSettingsService("/");
        CaptionParserImpl parser = new CaptionParserImpl(settingsService);

        ParsedCaption first = parser.parse(caption);
        String formatted = parser.format(first);
        ParsedCaption second = parser.parse(formatted);

        assertThat(second).isEqualTo(first);
    }

    @Property(tries = 100)
    void captionWhitespaceInvariant(
            @ForAll("captionParts") String brand,
            @ForAll("captionParts") String name,
            @ForAll("captionParts") String index,
            @ForAll("whitespaces") String spaces1,
            @ForAll("whitespaces") String spaces2,
            @ForAll("whitespaces") String spaces3) {

        SettingsService settingsService = createSettingsService("/");
        CaptionParserImpl parser = new CaptionParserImpl(settingsService);

        String captionWithoutSpaces = brand + "/" + name + "/" + index;
        String captionWithSpaces = spaces1 + brand + spaces1 + "/" + spaces2 + name + spaces2 + "/" + spaces3 + index + spaces3;

        ParsedCaption parsed1 = parser.parse(captionWithoutSpaces);
        ParsedCaption parsed2 = parser.parse(captionWithSpaces);

        assertThat(parsed2).isEqualTo(parsed1);
    }

    @Property(tries = 100)
    void alternativesCountMatchesCommas(@ForAll("alternativesList") List<String> alternatives) {
        SettingsService settingsService = createSettingsService("/");
        AlternativesParserImpl parser = new AlternativesParserImpl(settingsService);

        String input = String.join(", ", alternatives);
        List<AlternativeEntry> parsed = parser.parse(input);

        assertThat(parsed).hasSize(alternatives.size());
    }

    @Property(tries = 100)
    void captionTwoPartFormatIndexIsNull(
            @ForAll("captionParts") String brand,
            @ForAll("captionParts") String name) {

        SettingsService settingsService = createSettingsService("/");
        CaptionParserImpl parser = new CaptionParserImpl(settingsService);

        String caption = brand + "/" + name;
        ParsedCaption parsed = parser.parse(caption);

        assertThat(parsed.brand()).isEqualTo(brand);
        assertThat(parsed.name()).isEqualTo(name);
        assertThat(parsed.index()).isNull();
    }

    @Property(tries = 100)
    void captionSingleWordBrandIsNull(@ForAll("captionParts") String name) {
        SettingsService settingsService = createSettingsService("/");
        CaptionParserImpl parser = new CaptionParserImpl(settingsService);

        ParsedCaption parsed = parser.parse(name);

        assertThat(parsed.brand()).isNull();
        assertThat(parsed.name()).isEqualTo(name);
        assertThat(parsed.index()).isNull();
    }

    @Provide
    Arbitrary<String> captions() {
        return Arbitraries.oneOf(
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20)
                        .flatMap(brand -> Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20)
                                .flatMap(name -> Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20)
                                        .map(index -> brand + "/" + name + "/" + index))),
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20)
                        .flatMap(brand -> Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20)
                                .map(name -> brand + "/" + name)),
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20)
        );
    }

    @Provide
    Arbitrary<String> captionParts() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> whitespaces() {
        return Arbitraries.strings().withChars(" ").ofMinLength(0).ofMaxLength(3);
    }

    @Provide
    Arbitrary<List<String>> alternativesList() {
        Arbitrary<String> altEntry = Arbitraries.oneOf(
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(15)
                        .flatMap(brand -> Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(15)
                                .map(name -> brand + "/" + name)),
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(15)
        );
        return altEntry.list().ofMinSize(1).ofMaxSize(5);
    }
}