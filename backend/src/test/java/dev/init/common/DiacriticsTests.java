package dev.init.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiacriticsTests {

	@ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
	@CsvSource(delimiter = '|', textBlock = """
			ăâîșțţş                        | aaistts
			ĂÂÎȘȚŢŞ                        | AAISTTS
			Timișoara, Brașov, Iași        | Timisoara, Brasov, Iasi
			Practică de vară               | Practica de vara
			Ştiinţe                        | Stiinte
			Bucharest                      | Bucharest
			""")
	void removesRomanianDiacritics(String text, String expected) {
		assertThat(Diacritics.strip(text)).isEqualTo(expected);
	}

	@Test
	void coversBothCedillaAndCommaBelowForms() {
		// ș (comma below) and ş (cedilla) are different code points that look alike
		assertThat(Diacritics.strip("șşțţ")).isEqualTo("sstt");
		assertThat(Diacritics.strip("ȘŞȚŢ")).isEqualTo("SSTT");
	}

	@Test
	void leavesOtherCharactersAlone() {
		assertThat(Diacritics.strip("Café Müller – 100% ok")).isEqualTo("Café Müller – 100% ok");
	}

	@Test
	void handlesNullAndEmptyText() {
		assertThat(Diacritics.strip(null)).isNull();
		assertThat(Diacritics.strip("")).isEmpty();
	}

	@Test
	void everyReplacedCharacterHasAReplacement() {
		assertThat(Diacritics.FROM).hasSameSizeAs(Diacritics.TO);
	}

}
